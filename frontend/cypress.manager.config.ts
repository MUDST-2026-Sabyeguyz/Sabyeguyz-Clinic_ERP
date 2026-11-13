import { defineConfig } from 'cypress'
import process from 'node:process'

export default defineConfig({
  video: false,
  retries: 0,
  viewportWidth: 1440,
  viewportHeight: 1000,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'test-results/manager-[hash].xml' },
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/e2e-manager/**/*.cy.js',
    supportFile: 'cypress/support-manager/e2e.js',
    setupNodeEvents(_on, config) {
      if (process.env.MANAGER_E2E_RESET !== '1') {
        throw new Error('Set MANAGER_E2E_RESET=1 only when the app targets the disposable Cypress database on port 18080.')
      }
      if (config.baseUrl !== 'http://localhost:5173') {
        throw new Error('Manager E2E is restricted to the local Vite server on localhost:5173.')
      }
      if (process.env.VITE_API_PROXY_TARGET !== 'http://127.0.0.1:18080') {
        throw new Error('VITE_API_PROXY_TARGET must target the disposable Cypress backend on 127.0.0.1:18080.')
      }
      return config
    },
  },
})
