// Same entry point locally and in Actions. Only disposable, uniquely owned data is used.
import { spawn, spawnSync } from 'node:child_process'
import { randomBytes, createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import net from 'node:net'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
process.chdir(root)
const [major, minor] = process.versions.node.split('.').map(Number)
if (!((major === 22 && minor >= 18) || (major === 24 && minor >= 12) || major > 24)) {
  throw Error('Use Node 24.12+ (repo .nvmrc) or 22.18+. Node 20 is unsupported.')
}
process.env.PATH = path.dirname(process.execPath) + path.delimiter + process.env.PATH
const runId = new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomBytes(4).toString('hex')
const output = path.join(root, 'artifacts', 'phase1', runId)
fs.mkdirSync(output, { recursive: true })
const result = { runId, sourceSha: '', node: process.version, codeFingerprints: {}, steps: [], cleanup: {}, scope: 'Current clinicv2; synthetic PostgreSQL only', financialAcceptance: false }
let appId, dbId, networkId, backendStarted = false, vite
const prefix = 'clinic-phase1-check-' + randomBytes(6).toString('hex')
const browser = process.env.CYPRESS_BROWSER || 'electron'
const mvnImage = 'maven:3.9-eclipse-temurin-21'
const cli = (command, args, options = {}) => spawnSync(command, args, { cwd: root, encoding: 'utf8', windowsHide: true, ...options })
const docker = (args, options = {}) => cli('docker', args, options)
function checked(command, args, options) {
  const r = cli(command, args, options)
  if (r.status !== 0) throw Error(`${command} ${args[0]} failed (${r.status}): ${(r.stderr || r.error?.message || '').slice(-1000)}`)
  return r.stdout.trim()
}
function npmCli() {
  if (process.env.npm_execpath && fs.existsSync(process.env.npm_execpath)) return process.env.npm_execpath
  const r = cli(process.platform === 'win32' ? 'where.exe' : 'which', [process.platform === 'win32' ? 'npm.cmd' : 'npm'])
  for (const file of (r.stdout || '').trim().split(/\r?\n/)) {
    if (!file) continue
    for (const candidate of [path.join(path.dirname(file), 'node_modules/npm/bin/npm-cli.js'), fs.existsSync(file) ? fs.realpathSync(file) : '']) {
      if (candidate.endsWith('.js') && fs.existsSync(candidate)) return candidate
    }
  }
  throw Error('npm CLI not found; install Node with npm')
}
const npm = npmCli()
function stage(name, command, args, options = {}) {
  console.log(`\n[phase1] ${name}`)
  const log = path.join(output, name.replace(/[^a-z0-9-]/gi, '-') + '.log')
  const fd = fs.openSync(log, 'w')
  const started = Date.now()
  const r = cli(command, args, { stdio: ['ignore', fd, fd], ...options })
  fs.closeSync(fd)
  const entry = { name, startedUtc: new Date(started).toISOString(), exitCode: r.status ?? 1, seconds: (Date.now() - started) / 1000, log: path.relative(root, log).replaceAll('\\', '/') }
  result.steps.push(entry)
  console.log(`${entry.exitCode === 0 ? 'PASS' : 'FAIL'} ${name} (${entry.seconds.toFixed(1)}s)`)
  if (entry.exitCode !== 0) console.log(fs.readFileSync(log, 'utf8').split(/\r?\n/).slice(-22).join('\n'))
  return entry.exitCode === 0
}
const npmStage = (name, directory, args) => stage(name, process.execPath, [npm, ...args], { cwd: path.join(root, directory) })
const maven = (name, args) => stage(name, 'docker', ['exec', appId, 'mvn', '--batch-mode', '--no-transfer-progress', ...args])
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function free(port) {
  await new Promise((resolve, reject) => {
    const server = net.createServer()
    server.once('error', () => reject(Error(`Port ${port} is in use. Existing servers will not be stopped.`)))
    server.listen(port, '127.0.0.1', () => server.close(resolve))
  })
}
async function waitFor(url, child) {
  for (let i = 0; i < 90; i++) {
    if (child?.exitCode != null) throw Error('Owned Vite process exited before readiness')
    try { if ((await fetch(url, { signal: AbortSignal.timeout(2000) })).ok) return } catch {}
    await sleep(1000)
  }
  throw Error(`Readiness timed out: ${url}`)
}
function fingerprint(directory) {
  const hash = createHash('sha256')
  const files = checked('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', directory]).split('\0').filter(Boolean).sort()
  for (const file of files) { hash.update(file + '\0'); hash.update(fs.readFileSync(path.join(root, file))) }
  return hash.digest('hex')
}
function xmlCounts(files) {
  const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 }
  for (const file of files) for (const match of fs.readFileSync(file, 'utf8').matchAll(/<testsuite\b([^>]+)>/g)) {
    for (const key of Object.keys(totals)) totals[key] += Number(match[1].match(new RegExp(`\\b${key}="(\\d+)"`))?.[1] || 0)
  }
  return totals
}
function coverage() {
  const backend = path.join(root, 'backend/target/site/jacoco/jacoco.xml')
  const frontend = path.join(root, 'frontend/coverage/coverage-summary.json')
  if (result.steps.some(s => s.name === 'backend-final-coverage') && fs.existsSync(backend)) {
    const completeXml = fs.readFileSync(backend, 'utf8')
    const controller = completeXml.match(/<class name="com\/sabyeguyz\/clinicv2\/patient\/PatientController"[\s\S]*?<\/class>/)?.[0] || ''
    result.runtimePatientControllerLines = Number(controller.split('</method>').at(-1).match(/<counter type="LINE" missed="\d+" covered="(\d+)"\/>\s*<counter type="COMPLEXITY"/)?.[1] || 0)
    const xml = completeXml.split('</package>').at(-1)
    result.backendCoverage = Object.fromEntries([...xml.matchAll(/<counter type="([A-Z]+)" missed="(\d+)" covered="(\d+)"/g)].map(m => [m[1], { missed: Number(m[2]), covered: Number(m[3]), denominator: Number(m[2]) + Number(m[3]) }]))
    fs.cpSync(path.dirname(backend), path.join(output, 'backend-coverage'), { recursive: true })
  }
  if (result.steps.some(s => s.name === 'frontend-unit-coverage') && fs.existsSync(frontend)) {
    result.frontendCoverage = JSON.parse(fs.readFileSync(frontend, 'utf8')).total
    fs.cpSync(path.dirname(frontend), path.join(output, 'frontend-coverage'), { recursive: true })
  }
  const unit = path.join(root, 'backend/target/surefire-reports')
  if (result.steps.some(s => s.name === 'backend-unit-verify') && fs.existsSync(unit)) {
    result.backendUnit = xmlCounts(fs.readdirSync(unit).filter(f => /^TEST-.*\.xml$/.test(f)).map(f => path.join(unit, f)))
    fs.cpSync(unit, path.join(output, 'backend-unit-reports'), { recursive: true })
  }
  const frontendUnit = path.join(root, 'frontend/test-results/unit.xml')
  if (result.steps.some(s => s.name === 'frontend-unit-coverage') && fs.existsSync(frontendUnit)) {
    result.frontendUnit = xmlCounts([frontendUnit])
    fs.copyFileSync(frontendUnit, path.join(output, 'frontend-unit.xml'))
  }
  for (const name of ['backend-api', 'frontend-browser']) result[name] = xmlCounts(fs.readdirSync(output).filter(f => f.startsWith(name) && f.endsWith('.xml')).map(f => path.join(output, f)))
  result.coverageScope = { backend: 'All production com.sabyeguyz.clinicv2 classes; unit + instrumented PostgreSQL API/browser execution; legacy excluded', frontend: 'src/**/*.ts and .vue including unimported source; excludes declaration files and unit test files; unit-only V8, not browser coverage', thresholds: 'No numeric quality threshold has been approved; a report is not proof of requirement completeness' }
}

async function main() {
  result.sourceSha = checked('git', ['rev-parse', 'HEAD'])
  result.gitStatus = checked('git', ['status', '--porcelain'])
  result.codeFingerprints = { backend: fingerprint('backend'), frontend: fingerprint('frontend') }
  for (const port of [18080, 5173]) await free(port)
  if (!npmStage('frontend-install', 'frontend', ['ci', '--no-audit', '--no-fund'])) throw Error('Frontend dependency install failed')
  npmStage('frontend-lint', 'frontend', ['exec', '--', 'eslint', '.'])
  npmStage('frontend-oxlint', 'frontend', ['exec', '--', 'oxlint', '.'])
  npmStage('frontend-unit-coverage', 'frontend', ['run', 'test:coverage'])
  npmStage('frontend-typecheck-build', 'frontend', ['run', 'build'])
  if (!npmStage('backend-api-install', 'backend', ['ci', '--no-audit', '--no-fund'])) throw Error('Backend Cypress install failed')
  checked('docker', ['info', '--format', '{{.OSType}}'])
  networkId = checked('docker', ['network', 'create', '--label', 'clinic.phase1.run=' + runId, prefix])
  dbId = checked('docker', ['run', '-d', '--name', prefix + '-pg', '--network', networkId, '--label', 'clinic.phase1.run=' + runId, '-e', 'POSTGRES_DB=clinic', '-e', 'POSTGRES_USER=clinic', '-e', 'POSTGRES_PASSWORD=clinic_test_only', 'postgres:16-alpine'])
  let dbReady = false
  for (let i = 0; i < 60; i++) { if (docker(['exec', dbId, 'pg_isready', '-U', 'clinic', '-d', 'clinic']).status === 0) { dbReady = true; break }; await sleep(1000) }
  if (!dbReady) throw Error('Isolated PostgreSQL did not start')
  appId = checked('docker', ['run', '-d', '--name', prefix + '-app', '--network', networkId, '--label', 'clinic.phase1.run=' + runId, '-p', '127.0.0.1:18080:18080', '-e', 'DATABASE_URL=jdbc:postgresql://' + prefix + '-pg:5432/clinic', '-e', 'DATABASE_USERNAME=clinic', '-e', 'DATABASE_PASSWORD=clinic_test_only', '-e', 'SERVER_ADDRESS=0.0.0.0', '--mount', 'type=bind,src=' + path.join(root, 'backend') + ',dst=/app', '--mount', 'type=volume,src=sabyeguyz-m2,dst=/root/.m2', '-w', '/app', mvnImage, 'sleep', 'infinity'])
  if (!maven('backend-unit-verify', ['clean', 'verify'])) throw Error('Backend unit/build gate failed')
  backendStarted = true
  if (!maven('backend-v2-start', ['-Pcypress-v2', 'test-compile', 'spring-boot:start'])) throw Error('v2 test application failed to start')
  await waitFor('http://127.0.0.1:18080/actuator/health')
  const apiOutput = path.join(output, 'backend-api-[hash].xml').replaceAll('\\', '/')
  npmStage('backend-postgresql-api', 'backend', ['exec', '--', 'cypress', 'run', '--config-file', 'cypress.v2.config.cjs', '--browser', browser, '--reporter', 'junit', '--reporter-options', 'mochaFile=' + apiOutput])
  const viteLog = fs.openSync(path.join(output, 'vite.log'), 'w')
  vite = spawn(process.execPath, [path.join(root, 'frontend/node_modules/vite/bin/vite.js'), '--host', 'localhost', '--port', '5173', '--strictPort'], { cwd: path.join(root, 'frontend'), env: { ...process.env, VITE_API_PROXY_TARGET: 'http://127.0.0.1:18080' }, stdio: ['ignore', viteLog, viteLog], windowsHide: true, detached: true })
  fs.closeSync(viteLog)
  await waitFor('http://localhost:5173/', vite)
  const browserOutput = path.join(output, 'frontend-browser-[hash].xml').replaceAll('\\', '/')
  npmStage('frontend-browser-postgresql', 'frontend', ['exec', '--', 'cypress', 'run', '--config-file', 'cypress.v2.config.ts', '--browser', browser, '--reporter', 'junit', '--reporter-options', 'mochaFile=' + browserOutput])
}
try { await main() } catch (error) { result.fatalError = error.message; console.error(error.message) } finally {
  if (vite?.pid && vite.exitCode == null) {
    if (process.platform === 'win32') cli('taskkill.exe', ['/PID', String(vite.pid), '/T', '/F'])
    else { try { process.kill(-vite.pid, 'SIGTERM') } catch {} }
    result.cleanup.ownedViteStopped = true
  }
  if (appId) {
    if (backendStarted) maven('backend-v2-stop', ['-Pcypress-v2', 'spring-boot:stop'])
    maven('backend-final-coverage', ['jacoco:report'])
    docker(['logs', appId], { stdio: ['ignore', fs.openSync(path.join(output, 'maven-container.log'), 'w'), 'ignore'] })
  }
  try { coverage() } catch (error) { result.reportError = error.message }
  for (const id of [appId, dbId].filter(Boolean)) {
    const owned = docker(['inspect', id, '--format', '{{index .Config.Labels "clinic.phase1.run"}}'])
    if (owned.stdout?.trim() === runId) docker(['rm', '-f', '-v', id])
  }
  if (networkId) docker(['network', 'rm', networkId])
  result.cleanup.originalServicesTouched = false
  result.codeFingerprintsAfter = { backend: fingerprint('backend'), frontend: fingerprint('frontend') }
  result.codeUnchangedDuringRun = JSON.stringify(result.codeFingerprints) === JSON.stringify(result.codeFingerprintsAfter)
  result.success = !result.fatalError && !result.reportError && result.codeUnchangedDuringRun && result.steps.every(s => s.exitCode === 0) && result.backendUnit?.tests > 0 && result.frontendUnit?.tests > 0 && result['backend-api']?.tests > 0 && result['frontend-browser']?.tests > 0 && result.backendCoverage?.LINE?.covered > 0 && result.runtimePatientControllerLines > 0 && result.frontendCoverage?.lines?.covered > 0
  fs.writeFileSync(path.join(output, 'summary.json'), JSON.stringify(result, null, 2))
  if (process.env.GITHUB_STEP_SUMMARY) {
    const rows = [['Backend JUnit', result.backendUnit], ['Frontend Vitest', result.frontendUnit], ['Backend HTTP/PostgreSQL', result['backend-api']], ['Frontend browser/PostgreSQL', result['frontend-browser']]]
      .map(([name, counts]) => `| ${name} | ${counts?.tests ?? 'unverified'} | ${counts?.failures ?? '-'} | ${counts?.errors ?? '-'} | ${counts?.skipped ?? '-'} |`).join('\n')
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n## Phase 1 code/tests: ${result.success ? 'PASS' : 'FAIL'}\n\nChecked-out SHA: \`${result.sourceSha}\`. Code unchanged during run: ${result.codeUnchangedDuringRun}.\n\n| Suite | Tests | Failures | Errors | Skipped |\n|---|---:|---:|---:|---:|\n${rows}\n\nBackend line coverage: ${result.backendCoverage?.LINE?.covered ?? '?'}/${result.backendCoverage?.LINE?.denominator ?? '?'}. Frontend unit line coverage: ${result.frontendCoverage?.lines?.covered ?? '?'}/${result.frontendCoverage?.lines?.total ?? '?'}.\n\nFull logs, component fingerprints, JUnit and HTML/XML/LCOV reports are in the uploaded artifact. Coverage is partial; no approved numeric threshold. This gate does not certify full business acceptance or current-source Kubernetes deployment.\n`)
  }
  console.log('\nEvidence: ' + path.relative(root, output))
  console.log(result.success ? 'PASS: phase1 code/test/coverage checks (not full business acceptance)' : 'FAIL: inspect summary.json and the first failing stage log')
  process.exitCode = result.success ? 0 : 1
}
