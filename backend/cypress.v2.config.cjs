const { defineConfig } = require('cypress')
const fs = require('node:fs')
const path = require('node:path')
const parallelRequests = require('./cypress/tasks/parallel-requests.cjs')

// HTTP tests of the v2 application (ClinicV2Application on PostgreSQL).
module.exports = defineConfig({
  video: false,
  retries: 0,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/v2-[hash].xml' },
  e2e: {
    baseUrl: 'http://127.0.0.1:18080',
    specPattern: 'cypress/e2e-v2/**/*.cy.js',
    supportFile: 'cypress/support-v2/e2e.js',
    setupNodeEvents(on, config) {
      // This suite resets fixtures: never target a development/production server.
      if (config.baseUrl !== 'http://127.0.0.1:18080') {
        throw new Error('The v2 backend Cypress tests require the isolated server on 127.0.0.1:18080')
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
