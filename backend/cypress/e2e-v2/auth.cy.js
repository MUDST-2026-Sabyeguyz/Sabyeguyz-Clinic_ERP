describe('v2 auth and roles', () => {
  it('logs in a manager and returns a token and role', () => {
    cy.login('manager', 'manager123').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.username).to.equal('manager')
      expect(body.role).to.equal('MANAGER')
      expect(body.token).to.be.a('string').and.not.empty
    })
  })

  it('rejects a wrong password', () => {
    cy.login('manager', 'wrong-password').then(({ status }) => {
      expect([400, 401, 403]).to.include(status)
    })
  })

  it('rejects a request without a token', () => {
    cy.apiAs(null, 'GET', '/api/branches').its('status').should('equal', 401)
  })

  it('rejects a garbage token', () => {
    cy.apiAs('not-a-jwt', 'GET', '/api/branches').its('status').should('equal', 401)
  })

  it('lets a therapist read branches but not manage users', () => {
    cy.login('therapist', 'therapist123').then(({ body }) => {
      cy.apiAs(body.token, 'GET', '/api/branches').its('status').should('equal', 200)
      cy.apiAs(body.token, 'GET', '/api/users').its('status').should('equal', 403)
      cy.apiAs(body.token, 'POST', '/api/auth/register', { username: 'x', password: 'password123' })
        .its('status').should('equal', 403)
    })
  })

  it('lets a manager list users and register a new one who can log in', () => {
    cy.api('GET', '/api/users').its('status').should('equal', 200)
    cy.api('POST', '/api/auth/register', { username: 'newuser01', password: 'password123' }).then(({ status }) => {
      expect(status).to.equal(201)
    })
    cy.login('newuser01', 'password123').its('status').should('equal', 200)
  })
})
