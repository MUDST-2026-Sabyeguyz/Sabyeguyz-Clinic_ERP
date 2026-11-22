"""Remember refs on first run; subsequently emit every newly pushed commit once."""
import json
from pathlib import Path
import subprocess

state_file = Path('/var/jenkins_home/clinic-watch-state.json')
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()

refs = dict(line.split(' ', 1)[::-1] for line in git('for-each-ref', '--format=%(objectname) %(refname)', 'refs/remotes/origin').splitlines() if not line.endswith('/HEAD'))
old = json.loads(state_file.read_text()) if state_file.exists() else None
if old:
    emitted = set()
    old_shas = list(set(old.values()))
    for ref, sha in refs.items():
        if old.get(ref) == sha:
            continue
        for commit in git('rev-list', '--reverse', sha, '--not', *old_shas).splitlines():
            if commit not in emitted:
                emitted.add(commit)
                print(commit)
state_file.with_suffix('.pending.json').write_text(json.dumps(refs, indent=2))
