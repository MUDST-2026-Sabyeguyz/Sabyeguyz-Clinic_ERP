import './commands'

beforeEach(() => {
  cy.request('POST', '/api/auth/login', { username: 'manager', password: 'manager123' })
    .then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.token).to.be.a('string').and.not.be.empty
      cy.wrap(body.token, { log: false }).as('token')
    })
  cy.api('POST', '/__cypress/reset').then(({ status, body }) => {
    expect(status).to.equal(200)
    cy.wrap(body).as('fixtures')
  })
})
