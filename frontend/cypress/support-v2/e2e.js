import './commands'

// Every test starts from the same data: the backend resets the transactional tables and the reference data.
beforeEach(() => {
  cy.apiLogin('manager', 'manager123').then(({ status, body }) => {
    expect(status).to.equal(200)
    cy.wrap(body.token, { log: false }).as('managerToken')
  })
  cy.api('POST', '/__cypress/reset').then(({ status, body }) => {
    expect(status).to.equal(200)
    cy.wrap(body).as('fixtures')
  })
})
