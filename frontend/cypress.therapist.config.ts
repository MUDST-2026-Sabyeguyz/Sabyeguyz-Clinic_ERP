import { defineConfig } from 'cypress'
import process from 'node:process'

export default defineConfig({
  video: false,
  retries: 0,
  viewportWidth: 1440,
  viewportHeight: 1000,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'test-results/therapist-[hash].xml' },
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/e2e-therapist/**/*.cy.js',
    supportFile: 'cypress/support-therapist/e2e.js',
    setupNodeEvents(on) {
      if (process.env.THERAPIST_E2E_RESET !== '1') {
        throw new Error('Set THERAPIST_E2E_RESET=1 only when port 18080 uses the disposable patients_bcc database.')
      }
      on('after:spec', (spec, results) => {
        for (const test of results?.tests ?? []) {
          if (test.state === 'failed') console.log(`FAILED ${spec.name}: ${test.title.join(' > ')}\n${test.displayError}`)
        }
      })
    },
  },
})
