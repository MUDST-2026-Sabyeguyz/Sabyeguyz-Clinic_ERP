"""Execute the CI definitions stored in an immutable Git commit, under Jenkins.

This intentionally supports only the action adapters used by this repository.
Unknown actions/expressions fail explicitly; no test command is silently omitted.
Each workflow job gets a clean checkout, fresh service containers and a process
group which is terminated at job end. Docker lives in the dedicated CI daemon.
"""
import argparse
import datetime
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

import yaml


def command(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def expand(value, env, sha):
    text = str(value)
    def replace(match):
        expression = match.group(1).strip()
        if expression == 'github.sha':
            return sha
        if expression.startswith('env.'):
            return env.get(expression[4:], '')
        raise ValueError(f'Unsupported expression: {expression}')
    return re.sub(r'\$\{\{\s*(.*?)\s*\}\}', replace, text)


def update_env(file, env):
    lines = file.read_text().splitlines() if file.exists() else []
    index = 0
    while index < len(lines):
        line = lines[index]
        if '<<' in line:
            name, delimiter = line.split('<<', 1)
            index += 1
            values = []
            while index < len(lines) and lines[index] != delimiter:
                values.append(lines[index])
                index += 1
            env[name] = '\n'.join(values)
        elif '=' in line:
            name, value = line.split('=', 1)
            env[name] = value
        index += 1


def should_run(condition, failed, outcomes):
    condition = str(condition).replace('${{', '').replace('}}', '').strip()
    if condition in ('always()', '!cancelled()'):
        return True
    if condition == 'success()':
        return not failed
    if condition == 'failure()':
        return failed
    terms = condition.split('&&')
    comparisons = [re.fullmatch(r"\s*steps\.([a-zA-Z0-9_]+)\.outcome\s*==\s*'(success|failure|skipped)'\s*", term) for term in terms]
    if all(comparisons):
        return all(outcomes.get(match[1], 'skipped') == match[2] for match in comparisons)
    raise ValueError('Unsupported step condition: ' + condition)


def run_shell(script, cwd, env, log, timeout, processes):
    # Every step has its own session, so background Java/Vite processes can be
    # reaped without touching another job or the user's running applications.
    with log.open('w') as stream:
        process = subprocess.Popen(['bash', '--noprofile', '--norc', '-eo', 'pipefail', '-c', script], cwd=cwd, env=env,
                                   stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        processes.append(process.pid)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            raise RuntimeError(f'Step timed out after {timeout}s')
    if code:
        print('\n'.join(log.read_text(errors='replace').splitlines()[-25:]), flush=True)
        raise RuntimeError(f'Command exited {code}; see {log.name}')


def cypress_action(settings, cwd, env, log, timeout, processes):
    directory = cwd / settings.get('working-directory', '.')
    if settings.get('install', True) not in (False, 'false'):
        run_shell('npm ci', directory, env, log.with_suffix('.install.log'), timeout, processes)
    if settings.get('start'):
        with log.with_suffix('.server.log').open('w') as stream:
            process = subprocess.Popen(['bash', '-c', settings['start']], cwd=directory, env=env,
                                       stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
            processes.append(process.pid)
        deadline = time.monotonic() + int(settings.get('wait-on-timeout', 120))
        for url in settings.get('wait-on', '').split(','):
            if not url:
                continue
            while True:
                try:
                    with urllib.request.urlopen(url, timeout=2) as response:
                        if response.status < 400:
                            break
                except Exception:
                    pass
                if time.monotonic() > deadline:
                    raise RuntimeError(f'Cypress server not ready: {url}')
                time.sleep(1)
    browser = settings.get('browser', 'electron')
    # GitHub's chrome maps to installed Chromium; record this tool difference.
    if browser == 'chrome':
        browser = '/usr/bin/chromium'
    script = settings.get('command') or ('npx cypress run --browser ' + browser +
             (' --config-file ' + settings['config-file'] if settings.get('config-file') else ''))
    if settings.get('command') and '--browser chrome' in script:
        script = script.replace('--browser chrome', '--browser /usr/bin/chromium')
    run_shell(script, directory, env, log, timeout, processes)


def execute_job(workflow, job_id, job, args, base, output):
    key = workflow.stem + '-' + job_id
    work = base / key
    evidence = output / key
    evidence.mkdir(parents=True)
    command(['git', 'clone', '--quiet', '--no-checkout', '--shared', args.repository, str(work)])
    command(['git', '-C', str(work), 'checkout', '--quiet', '--detach', args.sha])
    env = dict(os.environ)
    env.update(CI='true', GITHUB_SHA=args.sha, GITHUB_WORKSPACE=str(work),
               GITHUB_EVENT_NAME='push',
               GITHUB_ENV=str(evidence / 'step.env'), GITHUB_STEP_SUMMARY=str(evidence / 'step-summary.md'),
               GITHUB_OUTPUT=str(evidence / 'step.output'), COMPOSE_PROJECT_NAME=f'clinic-replay-{os.environ.get("BUILD_NUMBER", "0")}-{key}'.lower(),
               CYPRESS_CACHE_FOLDER=str(Path.home() / '.cache/Cypress'),
               VITE_API_PROXY_TARGET='http://127.0.0.1:8080')
    result = {'workflow': str(workflow), 'job': job_id, 'sha': args.sha, 'steps': [], 'result': 'failure',
              'workflowSha256': hashlib.sha256((work / workflow).read_bytes()).hexdigest()}
    processes, services = [], []
    deadline = time.monotonic() + int(job.get('timeout-minutes', 60)) * 60
    try:
        if any(key in job for key in ('strategy', 'container', 'uses')):
            raise ValueError('Matrix/container/reusable jobs require an explicit adapter')
        definition = yaml.safe_load((work / workflow).read_text())
        for values in (definition.get('env', {}), job.get('env', {})):
            for name, value in values.items():
                env[name] = expand(value, env, args.sha)
        for name, service in job.get('services', {}).items():
            container = env['COMPOSE_PROJECT_NAME'] + '-' + name
            options = ['docker', 'run', '-d', '--name', container, '--label', 'clinic.replay=true']
            for name_, value in service.get('env', {}).items():
                options += ['-e', name_ + '=' + str(value)]
            for port in service.get('ports', []):
                options += ['-p', '127.0.0.1:' + str(port)]
            import shlex
            options += shlex.split(service.get('options', ''))
            services.append(container)
            command(options + [service['image']], stdout=subprocess.DEVNULL)
            for attempt in range(90):
                health = subprocess.check_output(['docker', 'inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}', container], text=True).strip()
                if health in ('healthy', 'none'):
                    break
                if attempt == 89:
                    raise RuntimeError('Service did not become healthy: ' + container)
                time.sleep(2)
        failed = False
        outcomes = {}
        for index, step in enumerate(job.get('steps', [])):
            name = step.get('name', step.get('uses', 'step-' + str(index)))
            entry = {'name': name, 'status': 'skipped'}
            result['steps'].append(entry)
            if step.get('id'):
                outcomes[step['id']] = 'skipped'
            try:
                enabled = should_run(step.get('if', 'success()'), failed, outcomes)
            except ValueError as error:
                entry.update(status='failure', error=str(error))
                failed = True
                continue
            if not enabled:
                continue
            print(f'[{key}] {name}', flush=True)
            started = time.monotonic()
            log = evidence / f'{index:02d}.log'
            try:
                update_env(Path(env['GITHUB_ENV']), env)
                step_env = dict(env)
                for name_, value in step.get('env', {}).items():
                    step_env[name_] = expand(value, step_env, args.sha)
                timeout = max(1, int(deadline - time.monotonic()))
                if 'run' in step:
                    defaults = {**definition.get('defaults', {}).get('run', {}), **job.get('defaults', {}).get('run', {})}
                    directory = work / step.get('working-directory', defaults.get('working-directory', '.'))
                    run_shell(expand(step['run'], step_env, args.sha), directory, step_env, log, timeout, processes)
                else:
                    action = step['uses'].split('@')[0]
                    settings = {k: expand(v, step_env, args.sha) if isinstance(v, str) else v for k, v in step.get('with', {}).items()}
                    if action == 'actions/checkout':
                        entry['adapter'] = 'Clean detached checkout of the original full SHA'
                    elif action in ('actions/setup-java', 'actions/setup-node', 'docker/setup-buildx-action'):
                        # Worker versions are recorded. No test/build command is skipped.
                        checks = {'actions/setup-java': ['java', '-version'], 'actions/setup-node': ['node', '--version'],
                                  'docker/setup-buildx-action': ['docker', 'buildx', 'version']}
                        with log.open('w') as stream:
                            command(checks[action], stdout=stream, stderr=subprocess.STDOUT)
                        entry['adapter'] = 'Preinstalled tool; version recorded in log'
                    elif action == 'actions/upload-artifact':
                        destination = evidence / ('artifacts-' + str(index))
                        destination.mkdir()
                        for pattern in settings.get('path', '').splitlines():
                            for file in work.glob(pattern.strip()):
                                if file.is_file():
                                    target = destination / file.relative_to(work)
                                    target.parent.mkdir(parents=True, exist_ok=True)
                                    shutil.copy2(file, target)
                                elif file.is_dir():
                                    shutil.copytree(file, destination / file.relative_to(work), dirs_exist_ok=True)
                        entry['adapter'] = 'Archived in Jenkins build artifacts'
                    elif action == 'cypress-io/github-action':
                        cypress_action(settings, work, step_env, log, timeout, processes)
                    else:
                        raise ValueError('Unsupported action (not silently skipped): ' + action)
                entry['status'] = 'success'
            except Exception as error:
                entry.update(status='failure', error=str(error))
                failed = True
                print(f'FAIL [{key}] {name}: {error}', flush=True)
            entry['seconds'] = round(time.monotonic() - started, 2)
            if step.get('id'):
                outcomes[step['id']] = entry['status']
        result['result'] = 'failure' if failed else 'success'
    except Exception as error:
        result['error'] = str(error)
        print(f'FAIL [{key}] {error}', flush=True)
    finally:
        for pid in processes:
            try:
                os.killpg(pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        # Scope cleanup to this job's unique Compose project and owned services.
        cleanup_env = dict(env)
        update_env(Path(env['GITHUB_ENV']), cleanup_env)
        if (work / 'compose.yaml').exists() or (work / 'docker-compose.yml').exists():
            with (evidence / 'cleanup.log').open('w') as stream:
                subprocess.run(['docker', 'compose', 'down', '-v', '--remove-orphans'], cwd=work, env=cleanup_env, stdout=stream, stderr=subprocess.STDOUT)
        for container in services:
            with (evidence / 'services.log').open('a') as stream:
                subprocess.run(['docker', 'logs', '--tail', '200', container], stdout=stream, stderr=subprocess.STDOUT)
            subprocess.run(['docker', 'rm', '-f', '-v', container], stdout=subprocess.DEVNULL)
        # Environment command files can contain temporary database secrets.
        for name in ('step.env', 'step.output'):
            (evidence / name).unlink(missing_ok=True)
        (evidence / 'result.json').write_text(json.dumps(result, indent=2))
        shutil.rmtree(work)
    return result


def main():
    def terminate(signum, frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, terminate)
    parser = argparse.ArgumentParser()
    parser.add_argument('--sha', required=True)
    parser.add_argument('--workflows', default='')
    parser.add_argument('--output', required=True)
    parser.add_argument('--repository', default='/var/jenkins_home/clinic-source.git')
    args = parser.parse_args()
    if not re.fullmatch('[a-f0-9]{40}', args.sha):
        raise ValueError('A full immutable commit SHA is required')
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    base = output.parent / ('work-' + output.name)
    base.mkdir(exist_ok=True)
    paths = subprocess.check_output(['git', '--git-dir=' + args.repository, 'ls-tree', '-r', '--name-only', args.sha, '.github/workflows'], text=True).splitlines()
    requested = [p for p in args.workflows.split(',') if p]
    workflows = [Path(p) for p in paths if p.endswith(('.yml', '.yaml')) and (not requested or p in requested or Path(p).name in requested)]
    if not workflows or any(not any(p == str(w) or p == w.name for w in workflows) for p in requested):
        raise ValueError('Requested workflows were not found at this SHA')
    summary = {'sha': args.sha, 'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'adapterSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               'worker': {'architecture': platform.machine(), 'system': platform.platform()},
               'scope': 'Original CI commands from this commit, replayed under Jenkins',
               'environmentDifferences': ['Debian worker instead of GitHub Ubuntu', 'Preinstalled Node 24 and Java 21', 'Chromium replaces Chrome in action adapter'], 'jobs': []}
    for workflow in workflows:
        raw = subprocess.check_output(['git', '--git-dir=' + args.repository, 'show', f'{args.sha}:{workflow.as_posix()}'], text=True)
        for job_id, job in yaml.safe_load(raw).get('jobs', {}).items():
            condition = job.get('if', '')
            if condition in ("github.event_name == 'workflow_dispatch'",
                             "github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/xan'"):
                summary['jobs'].append({'workflow': str(workflow), 'job': job_id, 'sha': args.sha, 'result': 'skipped', 'steps': [],
                    'reason': 'Manual job gate is false for push CI replay; run the dedicated workflow or local acceptance separately'})
            elif condition and condition != "github.event_name != 'pull_request' || github.event.pull_request.head.repo.full_name == github.repository":
                summary['jobs'].append({'workflow': str(workflow), 'job': job_id, 'sha': args.sha, 'result': 'failure', 'steps': [],
                    'error': 'Unsupported job condition: ' + str(condition)})
            else:
                summary['jobs'].append(execute_job(workflow, job_id, job, args, base, output))
            (output / 'summary.json').write_text(json.dumps(summary, indent=2))
    summary['result'] = 'success' if any(j['result'] == 'success' for j in summary['jobs']) and all(j['result'] in ('success', 'skipped') for j in summary['jobs']) else 'failure'
    summary['finishedAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    (output / 'summary.json').write_text(json.dumps(summary, indent=2))
    suite = ET.Element('testsuite', name='Original CI replay', tests=str(len(summary['jobs'])), failures=str(sum(j['result'] == 'failure' for j in summary['jobs'])))
    for job in summary['jobs']:
        test = ET.SubElement(suite, 'testcase', classname=job['workflow'], name=job['job'])
        if job['result'] == 'skipped':
            ET.SubElement(test, 'skipped', message=job['reason'])
        elif job['result'] != 'success':
            ET.SubElement(test, 'failure', message=job.get('error', 'CI step failed')).text = json.dumps(job['steps'], indent=2)
    ET.ElementTree(suite).write(output / 'summary.junit.xml', encoding='utf-8', xml_declaration=True)
    base.rmdir()
    print(json.dumps({'sha': args.sha, 'result': summary['result'], 'jobs': len(summary['jobs'])}), flush=True)
    return 0 if summary['result'] == 'success' else 1


if __name__ == '__main__':
    raise SystemExit(main())
