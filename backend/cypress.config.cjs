const { defineConfig } = require('cypress')
const fs = require('node:fs')
const path = require('node:path')
const parallelRequests = require('./cypress/tasks/parallel-requests.cjs')
const testPort = Number(process.env.CLINIC_API_TEST_PORT || 18080)
if (![18080, 18083].includes(testPort)) throw new Error('Only isolated API test ports 18080/18083 are supported')
const testBaseUrl = `http://127.0.0.1:${testPort}`

module.exports = defineConfig({
  video: false,
  retries: 0,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/api-[hash].xml' },
  e2e: {
    baseUrl: testBaseUrl,
    specPattern: 'cypress/e2e/**/*.cy.js',
    supportFile: 'cypress/support/e2e.js',
    setupNodeEvents(on, config) {
      // This suite resets fixtures: never target a development/production server.
      if (config.baseUrl !== testBaseUrl) {
        throw new Error(`Backend Cypress tests require the selected isolated server ${testBaseUrl}`)
      }
      on('task', { parallelRequests })
      on('before:run', () => {
        const results = path.join(__dirname, 'cypress', 'results')
        if (fs.existsSync(results)) {
          for (const entry of fs.readdirSync(results, { withFileTypes: true })) {
            if (entry.isFile() && /^api-[a-f0-9]+\.xml$/.test(entry.name)) {
              fs.unlinkSync(path.join(results, entry.name))
            }
          }
        }
      })
      return config
    },
  },
})
