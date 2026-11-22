const { spawnSync } = require('node:child_process')
const path = require('node:path')
const { existsSync } = require('node:fs')

process.chdir(path.resolve(__dirname, '..'))
const v2 = process.argv.includes('--v2')
function run(tool, args, env = process.env) {
  const result = spawnSync(tool, args, {
    stdio: 'inherit', env, shell: process.platform === 'win32', windowsHide: true,
  })
  if (result.error) throw result.error
  return result.status ?? 1
}

// Stop the forked Spring process before reporting, so the agent flushes its data.
const testStatus = run('npm', ['run', v2 ? 'test:v2' : 'test'], {
  ...process.env, CLINIC_TEST_COVERAGE: '1',
})
const reportStatus = run('mvn', ['--batch-mode', '--no-transfer-progress', '-Pcoverage', 'jacoco:report'])
const reportExists = existsSync('target/site/jacoco/jacoco.xml')
if (!reportExists) console.error('JaCoCo XML was not generated; coverage is unverified')
process.exitCode = testStatus || reportStatus || (reportExists ? 0 : 1)
