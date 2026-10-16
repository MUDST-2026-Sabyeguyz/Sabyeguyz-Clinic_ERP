/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai. */
describe('appointment branch isolation in the browser', () => {
  beforeEach(function () {
    for (const [branchCode, therapistId, firstName, identityNumber] of [
      ['01', this.fixtures.therapistId, 'สาขาหนึ่ง', '1100000000901'],
      ['02', this.fixtures.therapist2Id, 'สาขาสอง', '1100000000902'],
    ]) {
      cy.registerPatient({ branchCode, firstName, identityNumber }).then((patient) => {
        cy.api('POST', '/api/appointments', {
          branchCode, therapistId, patientId: patient.id, serviceId: this.fixtures.serviceId,
          startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`,
        }).its('status').should('equal', 201)
      })
    }
  })

  for (const [username, visible, hidden] of [
    ['therapist', 'สาขาหนึ่ง ใจดี', 'สาขาสอง ใจดี'],
    ['therapist2', 'สาขาสอง ใจดี', 'สาขาหนึ่ง ใจดี'],
  ]) {
    it(`${username} sees only appointments in the home branch`, () => {
      cy.intercept('GET', '**/api/appointments?*').as('appointments')
      cy.visitAs(username, '/staff/appointments')
      cy.wait('@appointments').its('response.body.totalItems').should('equal', 1)
      cy.get('tbody tr').should('have.length', 1).and('contain.text', visible)
      cy.get('tbody').should('not.contain.text', hidden)
    })
  }

  it('Manager sees appointments in both branches', () => {
    cy.visitAs('manager', '/staff/appointments')
    cy.get('tbody tr').should('have.length', 2)
    cy.get('tbody').should('contain.text', 'สาขาหนึ่ง ใจดี').and('contain.text', 'สาขาสอง ใจดี')
  })
})
