describe('backend integration', () => {
  it('loads the real backend health endpoint', () => {
    const backendUrl = Cypress.env('BACKEND_URL') || Cypress.env('backendUrl') || 'http://localhost:8080'
    cy.request(`${backendUrl}/actuator/health`)
      .its('body.status')
      .should('eq', 'UP')
  })

  it('opens the real frontend login screen', () => {
    cy.visit('/')
    cy.contains('เข้าสู่ระบบ').should('be.visible')
  })
})
