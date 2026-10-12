const { defineConfig } = require('cypress')
const fs = require('node:fs')
const path = require('node:path')
const parallelRequests = require('./cypress/tasks/parallel-requests.cjs')

module.exports = defineConfig({
  video: false,
  retries: 0,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/api-[hash].xml' },
  e2e: {
    baseUrl: 'http://127.0.0.1:18080',
    specPattern: 'cypress/e2e/**/*.cy.js',
    supportFile: 'cypress/support/e2e.js',
    setupNodeEvents(on, config) {
      // This suite resets fixtures: never target a development/production server.
      if (config.baseUrl !== 'http://127.0.0.1:18080') {
        throw new Error('Backend Cypress tests require the isolated server on 127.0.0.1:18080')
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
