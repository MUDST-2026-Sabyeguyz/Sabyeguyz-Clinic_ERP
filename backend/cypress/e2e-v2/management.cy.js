describe('v2 service and user management', () => {
  it('creates, updates and deactivates a single-visit service', () => {
    cy.api('POST', '/api/services', { code: 'MASSAGE_45', name: 'นวดบำบัด 45 นาที', defaultDurationMinutes: 45, price: 900 })
      .then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.code).to.equal('MASSAGE_45')
        expect(Number(body.price)).to.equal(900)
        cy.api('PUT', `/api/services/${body.id}`,
          { code: 'MASSAGE_45', name: 'นวดบำบัด 45 นาที', defaultDurationMinutes: 45, price: 950 })
          .then((updated) => {
            expect(updated.status).to.equal(200)
            expect(Number(updated.body.price)).to.equal(950)
          })
        cy.api('DELETE', `/api/services/${body.id}`).its('status').should('be.oneOf', [200, 204])
      })
  })

  it('rejects a duplicate service code', () => {
    cy.api('POST', '/api/services', { code: 'PHYSIO_30', name: 'ซ้ำ', defaultDurationMinutes: 30, price: 100 })
      .its('status').should('be.within', 400, 409)
  })

  it('creates and deactivates a course template', () => {
    cy.api('POST', '/api/courses',
      { code: 'COURSE_8', name: 'คอร์ส 8 ครั้ง', price: 7200, sessions: 8, bonusSessions: 1, validityDays: 120 })
      .then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.sessions).to.equal(8)
        expect(body.validityDays).to.equal(120)
        cy.api('GET', `/api/courses/${body.id}`).its('status').should('equal', 200)
        cy.api('DELETE', `/api/courses/${body.id}`).its('status').should('be.oneOf', [200, 204])
      })
  })

  it('refuses a therapist on the management endpoints', () => {
    cy.login('therapist', 'therapist123').then(({ body: auth }) => {
      cy.apiAs(auth.token, 'POST', '/api/services', { code: 'X', name: 'x', defaultDurationMinutes: 30, price: 1 })
        .its('status').should('equal', 403)
      cy.apiAs(auth.token, 'GET', '/api/users').its('status').should('equal', 403)
    })
  })

  it('creates a user with staff, role and branch access, who can then log in', () => {
    cy.api('POST', '/api/users', {
      username: 'newpt01', password: 'password123', role: 'THERAPIST', employeeCode: 'THP-900',
      email: 'newpt01@example.test', fullName: 'พีที ใหม่', branchCode: '01',
    }).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.role).to.equal('THERAPIST')
      expect(body.branchCode).to.equal('01')
      expect(body.email).to.equal('newpt01@example.test')
      cy.login('newpt01', 'password123').then((login) => {
        expect(login.status).to.equal(200)
        expect(login.body.role).to.equal('THERAPIST')
      })
      cy.api('DELETE', `/api/users/${body.id}`).its('status').should('be.oneOf', [200, 204])
      cy.login('newpt01', 'password123').its('status').should('be.within', 400, 403)
    })
  })

  it('rejects a user with a short password or a duplicate username', () => {
    const user = {
      username: 'newpt02', password: 'password123', role: 'THERAPIST', employeeCode: 'THP-901',
      email: 'newpt02@example.test', fullName: 'พีที สอง', branchCode: '01',
    }
    cy.api('POST', '/api/users', { ...user, password: 'short' }).its('status').should('equal', 400)
    cy.api('POST', '/api/users', user).its('status').should('equal', 201)
    cy.api('POST', '/api/users', { ...user, email: 'anotherpt@example.test', employeeCode: 'THP-902' })
      .its('status').should('be.within', 400, 409)
  })
})
