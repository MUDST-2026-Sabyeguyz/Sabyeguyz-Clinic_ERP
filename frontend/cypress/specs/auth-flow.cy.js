describe('Authentication and Session Flow', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'

  it('authenticates admin user with valid credentials', () => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      body: {
        username: 'admin',
        password: 'Admin@123456',
      },
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('token')
      expect(res.body).to.have.property('role', 'Manager')
      expect(res.body.token).to.be.a('string')
    })
  })

  it('rejects invalid password', () => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      body: {
        username: 'admin',
        password: 'wrong-password',
      },
      failOnStatusCode: false,
    }).its('status').should('be.oneOf', [400, 401])
  })

  it('rejects nonexistent user', () => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      body: {
        username: 'nonexistent_user_xyz',
        password: 'Password@123',
      },
      failOnStatusCode: false,
    }).its('status').should('be.oneOf', [400, 401])
  })
})
