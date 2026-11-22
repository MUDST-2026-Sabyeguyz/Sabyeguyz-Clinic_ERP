const { defineConfig } = require('cypress')
const fs = require('node:fs')
const path = require('node:path')
const parallelRequests = require('./cypress/tasks/parallel-requests.cjs')
const testPort = Number(process.env.CLINIC_V2_TEST_PORT || 18080)
if (![18080, 18082].includes(testPort)) {
  throw new Error('CLINIC_V2_TEST_PORT must be an isolated test port (18080 or 18082)')
}
const testBaseUrl = `http://127.0.0.1:${testPort}`

// HTTP tests of the v2 application (ClinicV2Application on PostgreSQL).
module.exports = defineConfig({
  video: false,
  retries: 0,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/v2-[hash].xml' },
  e2e: {
    baseUrl: testBaseUrl,
    specPattern: 'cypress/e2e-v2/**/*.cy.js',
    supportFile: 'cypress/support-v2/e2e.js',
    setupNodeEvents(on, config) {
      // This suite resets fixtures: never target a development/production server.
      if (config.baseUrl !== testBaseUrl) {
        throw new Error(`The v2 backend Cypress tests require the explicitly selected isolated server ${testBaseUrl}`)
      }
      on('task', { parallelRequests, courseRefundRace: require('./cypress/tasks/course-refund-race.cjs') })
      // The junit reporter writes failures to XML only; print them so they show up in the CI log.
      on('after:spec', (spec, results) => {
        for (const test of results?.tests ?? []) {
          if (test.state !== 'failed') continue
          console.log(`\nFAILED ${spec.relative} :: ${test.title.join(' > ')}`)
          console.log(`  ${(test.displayError || 'no error message').split('\n').slice(0, 12).join('\n  ')}`)
        }
      })
      on('before:run', () => {
        const results = path.join(__dirname, 'cypress', 'results')
        if (fs.existsSync(results)) {
          for (const entry of fs.readdirSync(results, { withFileTypes: true })) {
            if (entry.isFile() && /^v2-[a-f0-9]+\.xml$/.test(entry.name)) fs.unlinkSync(path.join(results, entry.name))
          }
        }
      })
      return config
    },
  },
})
