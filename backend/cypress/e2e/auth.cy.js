describe('Real JWT authentication', () => {
  it('rejects anonymous protected API requests', () => {
    cy.request({ url: '/api/patients', failOnStatusCode: false }).its('status').should('equal', 401)
  })

  it('rejects an invalid bearer token', () => {
    cy.request({ url: '/api/patients', headers: { Authorization: 'Bearer invalid-token' }, failOnStatusCode: false })
      .its('status').should('equal', 401)
  })

  it('allows a logged-in manager to access protected endpoints', () => {
    cy.api('GET', '/api/patients').its('status').should('equal', 200)
  })

  it('rejects incorrect credentials with the API error', () => {
    cy.request({ method: 'POST', url: '/api/auth/login', body: { username: 'manager', password: 'wrong-password' }, failOnStatusCode: false })
      .then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.message).to.equal('ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง')
      })
  })

  it('forbids a therapist from registering users or resetting fixtures', () => {
    cy.request('POST', '/api/auth/login', { username: 'therapist', password: 'therapist123' }).then(({ body }) => {
      for (const url of ['/api/auth/register', '/__cypress/reset']) {
        cy.request({ method: 'POST', url, headers: { Authorization: `Bearer ${body.token}` },
          body: { username: 'forbidden', password: 'password123' }, failOnStatusCode: false })
          .its('status').should('equal', 403)
      }
    })
  })
})
