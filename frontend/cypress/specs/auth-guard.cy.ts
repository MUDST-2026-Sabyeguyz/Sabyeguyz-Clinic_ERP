describe('backend authentication boundary', () => {
  it('rejects protected patient data without a token', () => {
    const backendUrl = Cypress.env('BACKEND_URL') || Cypress.env('backendUrl') || 'http://localhost:8080'

    cy.request({
      url: `${backendUrl}/api/patients`,
      failOnStatusCode: false,
    }).its('status').should('eq', 401)
  })
})
