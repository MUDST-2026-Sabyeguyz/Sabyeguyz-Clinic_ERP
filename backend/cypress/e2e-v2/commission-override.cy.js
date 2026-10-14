const month = new Date().toISOString().slice(0, 7)
const range = '?from=2020-01-01&to=2099-12-31'

function buyAndClose(fx, patient) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id, courseId: fx.courseId, branchCode: '01',
  }).then(({ body: purchase }) => {
    cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
    return cy.wrap(purchase)
  })
}

describe('v2 commission rate override and credited staff sales', () => {
  it('overrides the locked rate of a closed course and recalculates the pool', function () {
    cy.registerPatient().then((patient) => {
      buyAndClose(this.fixtures, patient).then((purchase) => {
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`,
          { rate: 0.08, reason: 'ข้อตกลงพิเศษ' }).then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)
          expect(Number(body.oldRate)).to.equal(0.05)
          expect(Number(body.newRate)).to.equal(0.08)
          expect(Number(body.commissionPool)).to.equal(720)
        })
        cy.api('GET', `/api/patients/${patient.id}/courses`).its('body.0.lockedCommissionRate')
          .should('satisfy', (rate) => Number(rate) === 0.08)
      })
    })
  })

  it('refuses an override before the course is locked, without a reason, or with a rate over 100%', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
      }).then(({ body: purchase }) => {
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 0.08, reason: 'x' })
          .its('status').should('equal', 400)
      })
    })
    cy.registerPatient({ firstName: 'สอง', phone: '0822222222', identityNumber: '1100000000102' }).then((patient) => {
      buyAndClose(this.fixtures, patient).then((purchase) => {
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 0.08, reason: '' })
          .its('status').should('equal', 400)
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 1.5, reason: 'x' })
          .its('status').should('equal', 400)
      })
    })
  })

  it('is manager-only and leaves an audit entry', function () {
    cy.registerPatient().then((patient) => {
      buyAndClose(this.fixtures, patient).then((purchase) => {
        cy.login('therapist', 'therapist123').then(({ body: auth }) => {
          cy.apiAs(auth.token, 'POST', `/api/commission/courses/${purchase.id}/override-rate`,
            { rate: 0.08, reason: 'x' }).its('status').should('equal', 403)
        })
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 0.07, reason: 'ปรับตามตกลง' })
          .its('status').should('equal', 200)
        cy.api('GET', '/api/audit-logs?size=100').its('body.items').then((items) => {
          const entry = items.find((item) => item.tableName === 'course' && /commission rate/.test(item.newValue || ''))
          expect(entry, 'override audit entry').to.exist
        })
      })
    })
  })

  it('credits a course sale to its seller (the Owner PT) in full in the staff sales report', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
        caseOwnerStaffId: this.fixtures.therapistId,
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/staff-sales${range}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        const amountOf = (needle) => Number(body.rows.find((row) => row.fullName.includes(needle)).amount)
        expect(amountOf('Therapist Demo')).to.equal(9000)
      })
    })
  })

  it('credits a single-visit sale to its salesperson in full', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01', salespersonStaffId: this.fixtures.therapistId,
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/staff-sales${range}`).then(({ body }) => {
        expect(body.rows).to.have.length(1)
        expect(Number(body.rows[0].amount)).to.equal(800)
      })
    })
  })
})
