// Endpoints the existing frontend (frontend/src/api/client.ts) calls that were missing in v2.
describe('v2 frontend contract gaps', () => {
  it('lists course usage transactions with their reversal status', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
      }).then(({ body: purchase }) => {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200)
        cy.api('GET', '/api/course-usages').then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body).to.have.length(1)
          expect(body[0].coursePurchaseId).to.equal(purchase.id)
          expect(body[0].patientHn).to.equal(patient.hn)
          expect(body[0].voidedAt).to.equal(null)
          cy.api('POST', `/api/course-movements/${body[0].id}/reverse`, { reason: 'ผิดคน' }).its('status').should('equal', 200)
        })
        cy.api('GET', '/api/course-usages').then(({ body }) => {
          expect(body[0].voidedAt).to.be.a('string')
          expect(body[0].voidReason).to.equal('ผิดคน')
        })
      })
    })
  })

  it('deactivates a staff member and refuses a therapist doing it', function () {
    cy.api('DELETE', `/api/staff/${this.fixtures.therapist2Id}`).its('status').should('equal', 204)
    cy.api('GET', `/api/staff/${this.fixtures.therapist2Id}`).its('body.active').should('equal', false)
    cy.login('therapist', 'therapist123').then(({ body: auth }) => {
      cy.apiAs(auth.token, 'DELETE', `/api/staff/${this.fixtures.therapistId}`).its('status').should('equal', 403)
    })
  })

  it('records a termination with its commission policy', function () {
    cy.api('POST', `/api/staff/${this.fixtures.therapist2Id}/terminate`, {
      terminationDate: this.fixtures.tomorrow, commissionAfterTerminationPolicy: 'CONTINUE_UNTIL_COMPLETE',
    }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.active).to.equal(false)
    })
    cy.api('POST', `/api/staff/${this.fixtures.therapistId}/terminate`, {
      terminationDate: this.fixtures.tomorrow, commissionAfterTerminationPolicy: 'NOT_A_POLICY',
    }).its('status').should('equal', 400)
  })
})
