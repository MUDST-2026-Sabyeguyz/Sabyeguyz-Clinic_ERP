import { defineConfig } from 'cypress'
import { readFileSync } from 'node:fs'
import process from 'node:process'

export default defineConfig({
  video: false,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/k8s-[hash].xml' },
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://127.0.0.1:18088',
    specPattern: 'cypress/k8s/**/*.cy.ts',
    supportFile: false,
    setupNodeEvents(on, config) {
      const url = new URL(config.baseUrl ?? '')
      if (!['localhost', '127.0.0.1'].includes(url.hostname)) {
        throw new Error('Kubernetes acceptance is limited to a local forwarded Ingress')
      }
      on('task', {
        managerCredentials() {
          const file = process.env.CLINIC_K8S_CREDENTIALS
          if (!file) throw new Error('Set CLINIC_K8S_CREDENTIALS to the private runtime credentials JSON')
          const credentials = JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
          return { username: credentials.MANAGER_USERNAME, password: credentials.MANAGER_PASSWORD }
        },
      })
      return config
    },
  },
})
