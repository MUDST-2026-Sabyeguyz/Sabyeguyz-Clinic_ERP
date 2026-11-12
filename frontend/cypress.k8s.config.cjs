const { defineConfig } = require('cypress')
const { readFileSync, existsSync, readdirSync, unlinkSync } = require('node:fs')
const path = require('node:path')

module.exports = defineConfig({
  video: false,
  reporter: 'junit',
  reporterOptions: { mochaFile: 'cypress/results/k8s-[hash].xml' },
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://127.0.0.1:18088',
    specPattern: 'cypress/k8s/**/*.cy.js',
    supportFile: false,
    setupNodeEvents(on, config) {
      const url = new URL(config.baseUrl ?? '')
      if (!['localhost', '127.0.0.1'].includes(url.hostname)) {
        throw new Error('Kubernetes acceptance is limited to a local forwarded Ingress')
      }
      on('before:run', () => {
        const results = path.join(__dirname, 'cypress/results')
        if (!existsSync(results)) return
        for (const entry of readdirSync(results, { withFileTypes: true })) {
          if (entry.isFile() && /^k8s-[a-f0-9]+\.xml$/.test(entry.name)) {
            unlinkSync(path.join(results, entry.name))
          }
        }
      })
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
