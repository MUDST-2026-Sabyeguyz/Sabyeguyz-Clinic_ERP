const { spawnSync } = require('node:child_process')
const net = require('node:net')
const path = require('node:path')
const cypress = require('cypress')
const testPort = Number(process.env.CLINIC_V2_TEST_PORT || 18080)
if (![18080, 18082].includes(testPort)) {
  throw new Error('CLINIC_V2_TEST_PORT must be an isolated test port (18080 or 18082)')
}

process.chdir(path.resolve(__dirname, '..'))

// Starts the v2 application (mvn -Pcypress-v2) on the selected isolated loopback port against PostgreSQL given by
// DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD, runs the v2 Cypress specs, then stops it.
function maven(goal) {
  const result = spawnSync('mvn', ['--batch-mode', '--no-transfer-progress', '-Pcypress-v2', ...goal], {
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
    throw new Error('Usage: npm run test:v2 -- [--spec cypress/e2e-v2/auth.cy.js] or npm run test:v2:open')
  }
  if (!process.env.DATABASE_URL) throw new Error('Set DATABASE_URL (jdbc:postgresql://...) for the v2 tests')
  await requireFreePort(testPort)
  await requireFreePort(19001)
  try {
    maven(['test-compile', 'spring-boot:start'])
    const configFile = 'cypress.v2.config.cjs'
    if (interactive) {
      await cypress.open({ project: process.cwd(), configFile })
    } else {
      const result = await cypress.run({
        project: process.cwd(),
        configFile,
        browser: 'electron',
        ...(args[0] === '--spec' ? { spec: args[1] } : {}),
      })
      if (result.status === 'failed' || result.totalFailed || !result.totalTests) process.exitCode = 1
    }
  } finally {
    maven(['spring-boot:stop'])
  }
}

main().catch((error) => {
  console.error(error.message)
  process.exitCode = 1
})
