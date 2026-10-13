/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai. */
describe('authenticated patient readback with real PostgreSQL backend', () => {
  it('logs in through the UI and displays an API-created patient after reload', () => {
    const base = new URL(Cypress.config('baseUrl'))
    const prefix = base.pathname.replace(/\/$/, '')
    const url = (path) => `${base.origin}${prefix}${path}`
    cy.env(['demoUsername', 'demoPassword'], { log: false }).then(({ demoUsername, demoPassword }) => {
      expect(demoUsername, 'synthetic Manager fixture').to.be.a('string').and.not.be.empty
      expect(demoPassword, 'synthetic Manager password').to.be.a('string').and.not.be.empty
      cy.visit(url('/'))
      cy.get('input[autocomplete="username"]').type(demoUsername, { log: false })
      cy.get('input[autocomplete="current-password"]').type(demoPassword, { log: false })
      cy.get('button[type="submit"]').click()
      cy.location('pathname').should('eq', `${prefix}/staff/dashboard`)
      cy.window().then((win) => {
        const token = win.localStorage.getItem('authToken')
        const suffix = `${Date.now()}-${Cypress._.random(10000, 99999)}`
        cy.request({
          method: 'POST', url: url('/api/patients'), log: false,
          headers: { Authorization: `Bearer ${token}` },
          body: {
            branchCode: 'RP', username: `e2e-${suffix}`, email: `e2e-${suffix}@example.invalid`,
            password: 'synthetic-test-password', title: 'นาย', fullName: 'ทดสอบ ระบบเบราว์เซอร์',
            birthDate: '1990-01-01', gender: 'ชาย', phone: '0800000000',
            identityDocumentType: 'PASSPORT', identityNumber: `E2E${suffix}`, address: 'Synthetic data',
            drugAllergy: false, foodAllergy: false, chronicDisease: '',
            emergencyContactName: 'Test', emergencyContactRelationship: 'Test',
            emergencyContactPhone: '0800000001', privacyConsent: true,
          },
        }).then(({ status, body }) => {
          expect(status).to.eq(201)
          cy.visit(url(`/staff/patients/${body.id}`))
          cy.contains('ทดสอบ ระบบเบราว์เซอร์').should('be.visible')
          cy.contains(body.hn).should('be.visible')
          cy.reload()
          cy.contains('ทดสอบ ระบบเบราว์เซอร์').should('be.visible')
          cy.contains(body.hn).should('be.visible')
          // An expired session must return to this application's prefixed login.
          cy.intercept('GET', url(`/api/patients/${body.id}`)).as('expiredSession')
          cy.window().then((win) => win.localStorage.setItem('authToken', 'invalid'))
          cy.reload()
          cy.wait('@expiredSession').its('response.statusCode').should('eq', 401)
          cy.location('pathname').should('eq', `${prefix}/`)
          cy.get('input[autocomplete="username"]').should('be.visible')
        })
      })
    })
  })
})
