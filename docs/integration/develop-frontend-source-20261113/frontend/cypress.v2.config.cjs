const { defineConfig } = require('cypress')
const { appendFileSync, existsSync, readdirSync, unlinkSync } = require('node:fs')
const path = require('node:path')

// Browser tests of the staff web app against the real v2 backend on PostgreSQL (see docs/frontend-e2e-tests.md).
// Before every test the backend fixture endpoint resets the data, exactly like the backend HTTP suite.
module.exports = defineConfig({
  video: false,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/browser-v2-[hash].xml' },
  retries: 0,
  viewportWidth: 1440,
  viewportHeight: 900,
  defaultCommandTimeout: 8000,
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://localhost:5173',
    specPattern: 'cypress/e2e-v2/**/*.cy.js',
    supportFile: 'cypress/support-v2/e2e.js',
    expose: { apiUrl: process.env.CYPRESS_API_URL ?? 'http://127.0.0.1:18080' },
    setupNodeEvents(on) {
      on('before:run', () => {
        const results = path.join(__dirname, 'cypress/results')
        if (!existsSync(results)) return
        for (const entry of readdirSync(results, { withFileTypes: true })) {
          if (entry.isFile() && /^browser-v2-[a-f0-9]+\.xml$/.test(entry.name)) {
            unlinkSync(path.join(results, entry.name))
          }
        }
      })
      // The default reporter hides the reason of a failure in CI logs that are long; print it in a searchable form.
      on('after:spec', (spec, results) => {
        for (const test of results?.tests ?? []) {
          if (test.state !== 'failed') continue
          console.log(`\nFAILED ${spec.relative} :: ${test.title.join(' > ')}`)
          console.log(`  ${(test.displayError || 'no error message').split('\n').slice(0, 10).join('\n  ')}`)
          // Also on the run's summary page, which lists every failure (the log view can hide some).
          if (process.env.GITHUB_STEP_SUMMARY) {
            const reason = (test.displayError || '').split('\n').slice(0, 4).join('\n')
            appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n\`\`\`\nFAILED ${spec.relative} :: ${test.title.join(' > ')}\n${reason}\n\`\`\`\n`)
          }
        }
      })
    },
  },
})
