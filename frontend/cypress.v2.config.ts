import { defineConfig } from 'cypress'
import process from 'node:process'

// Browser tests of the staff web app against the real v2 backend on PostgreSQL (see docs/frontend-e2e-tests.md).
// Before every test the backend fixture endpoint resets the data, exactly like the backend HTTP suite.
export default defineConfig({
  video: false,
  retries: 0,
  viewportWidth: 1440,
  viewportHeight: 900,
  defaultCommandTimeout: 8000,
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://localhost:5173',
    specPattern: 'cypress/e2e-v2/**/*.cy.js',
    supportFile: 'cypress/support-v2/e2e.js',
    env: { apiUrl: process.env.CYPRESS_API_URL ?? 'http://127.0.0.1:18080' },
    setupNodeEvents(on) {
      // The default reporter hides the reason of a failure in CI logs that are long; print it in a searchable form.
      on('after:spec', (spec, results) => {
        for (const test of results?.tests ?? []) {
          if (test.state !== 'failed') continue
          console.log(`\nFAILED ${spec.relative} :: ${test.title.join(' > ')}`)
          console.log(`  ${(test.displayError || 'no error message').split('\n').slice(0, 10).join('\n  ')}`)
        }
      })
    },
  },
})
