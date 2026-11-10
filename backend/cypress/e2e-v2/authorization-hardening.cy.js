// Security audit Phase 1: role and branch enforcement that must not depend on the UI hiding buttons.
describe('authorization hardening (audit Phase 1)', () => {
  beforeEach(function () {
    cy.login('therapist', 'therapist123').its('body.token').as('therapist')
    cy.login('manager', 'manager123').its('body.token').as('manager')
  })

  it('H1: a therapist cannot create, change or deactivate course templates', function () {
    const body = { code: 'SEC-1', name: 'Security probe', price: 1000, sessions: 1, validity: 30, unit: 'Day', duration: 30 }
    cy.apiAs(this.therapist, 'POST', '/api/courses', body).its('status').should('equal', 403)
    cy.apiAs(this.therapist, 'PUT', '/api/courses/1', body).its('status').should('equal', 403)
    cy.apiAs(this.therapist, 'DELETE', '/api/courses/1').its('status').should('equal', 403)
    cy.apiAs(this.therapist, 'PATCH', '/api/courses/1/activate').its('status').should('equal', 403)
    cy.apiAs(this.therapist, 'GET', '/api/courses').its('status').should('equal', 200)
  })

  it('H3: reports and dashboard are Manager-only', function () {
    const range = 'from=2026-01-01&to=2026-12-31'
    for (const path of ['/api/dashboard/summary', '/api/reports/revenue', '/api/reports/appointments',
      '/api/reports/course-balances', '/api/reports/staff-sales']) {
      cy.apiAs(this.therapist, 'GET', `${path}?${range}`).its('status').should('equal', 403)
      cy.apiAs(this.manager, 'GET', `${path}?${range}`).its('status').should('equal', 200)
    }
  })

  it('H4: a branch-01 therapist cannot register a patient or sell a course in branch 02', function () {
    cy.apiAs(this.therapist, 'POST', '/api/patients', { branchCode: '02', firstName: 'Sec', lastName: 'Probe' })
      .its('status').should('equal', 403)
    cy.apiAs(this.therapist, 'POST', '/api/course-purchases', { patientId: 1, courseId: 1, branchCode: '02' })
      .its('status').should('equal', 403)
  })

  it('M1: repeated bad logins return 401 and then 429 for that account only', function () {
    const name = `probe-${Date.now()}`
    for (let attempt = 0; attempt < 5; attempt += 1) {
      cy.api('POST', '/api/auth/login', { username: name, password: 'wrong-password' })
        .its('status').should('equal', 401)
    }
    cy.api('POST', '/api/auth/login', { username: name, password: 'wrong-password' })
      .its('status').should('equal', 429)
    cy.login('therapist', 'therapist123').its('status').should('equal', 200)
  })

  it('L1: change-password rejects a wrong current or short new password', function () {
    cy.apiAs(this.therapist, 'POST', '/api/auth/change-password',
      { currentPassword: 'not-the-password', newPassword: 'a-long-new-password-1' }).its('status').should('equal', 400)
    cy.apiAs(this.therapist, 'POST', '/api/auth/change-password',
      { currentPassword: 'therapist123', newPassword: 'short' }).its('status').should('equal', 400)
    cy.api('POST', '/api/auth/change-password', { currentPassword: 'x', newPassword: 'y' })
      .its('status').should('equal', 401)
  })

  it('L1: user management requires a password of at least 12 characters', function () {
    const user = (suffix, password) => ({
      username: `pwlen-${suffix}-${Date.now()}`, email: `pwlen-${suffix}-${Date.now()}@example.com`, password,
      role: 'THERAPIST', employeeCode: `PW-${suffix}-${Date.now() % 100000}`, fullName: 'Pw Len', branchCode: '01',
    })
    cy.apiAs(this.manager, 'POST', '/api/users', user('a', 'elevenchars')).its('status').should('equal', 400)
    cy.apiAs(this.manager, 'POST', '/api/users', user('b', 'twelvechars1')).its('status').should('equal', 201)
  })
})
