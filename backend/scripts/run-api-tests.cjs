const { spawnSync } = require('node:child_process')
const net = require('node:net')
const path = require('node:path')
const cypress = require('cypress')

process.chdir(path.resolve(__dirname, '..'))

function maven(goal) {
  const profile = process.env.CLINIC_TEST_COVERAGE === '1' ? '-Pcypress-api,coverage' : '-Pcypress-api'
  const result = spawnSync('mvn', ['--batch-mode', '--no-transfer-progress', profile, ...goal], {
    stdio: 'inherit',
    shell: process.platform === 'win32',
    windowsHide: true,
  })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`Maven ${goal.join(' ')} failed (${result.status})`)
}

function requireFreePort(port) {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.once('error', () => reject(new Error(`Port ${port} is in use; refusing to start or stop an existing server`)))
    server.listen(port, '127.0.0.1', () => server.close(resolve))
  })
}

async function main() {
  const args = process.argv.slice(2)
  const interactive = args[0] === '--open'
  if (args.length && !(interactive && args.length === 1) && !(args[0] === '--spec' && args.length === 2)) {
    throw new Error('Usage: npm run test:api -- [--spec cypress/e2e/patients.cy.js] or npm run test:api:open')
  }
  await requireFreePort(18080)
  await requireFreePort(19001)
  let started = false
  try {
    // Mark before startup so a timeout/partial start still gets cleaned up.
    started = true
    maven(['test-compile', 'spring-boot:start'])
    if (interactive) {
      await cypress.open({ project: process.cwd() })
    } else {
      const result = await cypress.run({
        project: process.cwd(),
        browser: 'electron',
        ...(args[0] === '--spec' ? { spec: args[1] } : {}),
      })
      if (result.status === 'failed' || result.totalFailed || !result.totalTests) process.exitCode = 1
    }
  } finally {
    if (started) maven(['spring-boot:stop'])
  }
}

main().catch((error) => {
  console.error(error.message)
  process.exitCode = 1
})
