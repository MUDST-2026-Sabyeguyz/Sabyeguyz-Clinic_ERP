import { defineConfig } from 'cypress'
import process from 'node:process'

export default defineConfig({
  video: false,
  retries: 0,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'test-results/patients-bcc.xml' },
  viewportWidth: 1440,
  viewportHeight: 1000,
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/e2e-patients/patients-bcc.cy.js',
    supportFile: false,
    setupNodeEvents() {
      // Reset truncates transactional tables. Require a deliberate isolated-test run.
      if (process.env.PATIENTS_BCC_RESET !== '1') {
        throw new Error('Set PATIENTS_BCC_RESET=1 only with the disposable cypress-v2 database on port 18080. See docs/testing/patients-bcc.md.')
      }
    },
  },
})
