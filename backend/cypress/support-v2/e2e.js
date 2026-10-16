import './commands'

beforeEach(() => {
  cy.login('manager', 'manager123').then(({ status, body }) => {
    expect(status).to.equal(200)
    cy.wrap(body.token, { log: false }).as('token')
  })
  cy.api('POST', '/__cypress/reset').then(({ status, body }) => {
    expect(status).to.equal(200)
    cy.wrap(body).as('fixtures')
  })
})
