const month = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit',
}).format(new Date())
const following = (value) => {
  const [year, number] = value.split('-').map(Number)
  return new Date(Date.UTC(year, number, 1)).toISOString().slice(0, 7)
}
const nextMonth = following(month)
const money = (value) => Math.round(Number(value) * 100) / 100

const prepare = (value) => cy.api('POST', `/api/commission/payments/prepare?month=${value}&branchCode=01`)
const close = (value) => cy.api('POST', `/api/commission/payments/close?month=${value}&branchCode=01`)

function paidSource(staffId) {
  return cy.api('POST', '/api/courses', {
    code: 'APPROVAL-ADJ', name: 'คอร์สทดสอบปรับยอด', price: 10000, sessions: 10,
    bonusSessions: 0, validityDays: 365,
  }).then(({ body: service }) => cy.registerPatient().then((patient) =>
    cy.api('POST', '/api/course-purchases', {
      patientId: patient.id, courseId: service.id, branchCode: '01',
      caseOwnerStaffId: staffId, paymentLabelCode: 'FULL',
    }).then(({ body: course }) => {
      cy.useCourseSession(course.id)
      cy.api('POST', `/api/commission/closings/${month}?branchCode=01`)
      prepare(month).then(({ body: payments }) => {
        const payment = payments.find((row) => row.staffId === staffId)
        expect(payment).to.exist
        close(month)
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`)
      })
      return cy.api('GET', `/__cypress/commission/courses/${course.id}/allocations`)
        .then(({ body }) => body[0].allocation_id)
    })))
}

// An adjustment and its scheduled application both require Manager approval.
const application = (id, value, branchCode = '01') => cy.api(
  'POST', `/api/commission/adjustments/${id}/applications`, { month: value, branchCode },
)
const paymentFor = (staffId, value) => prepare(value).then(({ status, body }) => {
  expect(status).to.equal(200)
  const payment = body.find((row) => row.staffId === staffId)
  expect(payment).to.exist
  return payment
})

describe('Manager-approved adjustment from a paid source', () => {
  it('keeps an addition out of payroll until both approvals and records the paid month', function () {
    const staffId = this.fixtures.therapistId
    paidSource(staffId).then((allocationId) => {
      cy.api('POST', '/api/commission/adjustments', {
        staffId, allocationId, originalMonth: month, appliedMonth: nextMonth,
        amount: 50, reason: 'Correct paid commission',
      }).then(({ status, body: adjustment }) => {
        expect(status).to.equal(201)
        expect(adjustment.status).to.equal('Pending')
        expect(adjustment.applications).to.have.length(0)
        application(adjustment.id, nextMonth).its('status').should('equal', 400)
        cy.login('therapist', 'therapist123').then(({ body: auth }) => {
          cy.apiAs(auth.token, 'POST', `/api/commission/adjustments/${adjustment.id}/approve`)
            .its('status').should('equal', 403)
        })
        cy.api('POST', `/api/commission/adjustments/${adjustment.id}/approve`)
          .its('body.status').should('equal', 'Approved')
        // Source-branch checks apply when the Manager selects a payment for the application.
        application(adjustment.id, nextMonth, '02').its('status').should('equal', 400)
        application(adjustment.id, nextMonth).then(({ status: created, body: app }) => {
          expect(created).to.equal(201)
          expect(app.approved).to.equal(false)
          paymentFor(staffId, nextMonth).then((payment) => expect(money(payment.adjustment)).to.equal(0))
          cy.login('therapist', 'therapist123').then(({ body: auth }) => {
            cy.apiAs(auth.token, 'POST', `/api/commission/adjustment-applications/${app.id}/approve`, {})
              .its('status').should('equal', 403)
          })
          cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, {})
            .its('status').should('equal', 200)
          paymentFor(staffId, nextMonth).then((payment) => {
            expect(money(payment.adjustment)).to.equal(50)
            close(nextMonth).its('status').should('equal', 200)
            cy.api('POST', `/api/commission/payments/${payment.id}/pay`).then(({ status, body: paid }) => {
              expect(status).to.equal(200)
              expect(paid.month).to.equal(nextMonth)
              expect(paid.status).to.equal('PAID')
            })
          })
        })
        cy.api('GET', `/api/commission/adjustments/${adjustment.id}`).then(({ body: done }) => {
          expect(done.status).to.equal('Applied')
          expect(money(done.processed)).to.equal(50)
          expect(money(done.remaining)).to.equal(0)
          expect(done.applications[0].status).to.equal('Applied')
        })
        paymentFor(staffId, month).then((original) => {
          expect(original.status).to.equal('PAID')
          expect(money(original.adjustment)).to.equal(0)
        })
      })
    })
  })

  it('requires consent for a deduction, carries its balance and cancels unpaid applications on resignation', function () {
    const staffId = this.fixtures.therapistId
    const laterMonth = following(nextMonth)
    paidSource(staffId).then((allocationId) => {
      cy.api('POST', '/api/commission/adjustments', {
        staffId, allocationId, originalMonth: month, appliedMonth: nextMonth,
        amount: -500, reason: 'Correct overpaid commission',
      }).then(({ status, body: adjustment }) => {
        expect(status).to.equal(201)
        expect(adjustment.status).to.equal('Pending')
        application(adjustment.id, nextMonth).its('status').should('equal', 400)
        cy.api('POST', `/api/commission/adjustments/${adjustment.id}/approve`).its('status').should('equal', 200)
        application(adjustment.id, nextMonth).then(({ status: created, body: app }) => {
          expect(created).to.equal(201)
          expect(app.approved).to.equal(false)
          cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, {})
            .its('status').should('equal', 400)
          cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, { reference: 'AGREEMENT-1' })
            .its('status').should('equal', 200)
        })
        paymentFor(staffId, nextMonth).then((payment) => {
          // No earnings this month: an approved deduction cannot create a negative payment.
          expect(money(payment.adjustment)).to.equal(0)
          close(nextMonth).its('status').should('equal', 200)
          cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 200)
        })
        cy.api('GET', `/api/commission/adjustments/${adjustment.id}`).then(({ body: remaining }) => {
          expect(money(remaining.processed)).to.equal(0)
          expect(money(remaining.remaining)).to.equal(500)
        })
        // Carry-over is explicitly scheduled and approved, never silently applied.
        application(adjustment.id, laterMonth).then(({ status: created, body: app }) => {
          expect(created).to.equal(201)
          expect(app.approved).to.equal(false)
          cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, { reference: 'AGREEMENT-1' })
            .its('status').should('equal', 200)
        })
        cy.api('POST', `/api/staff/${staffId}/terminate`, { terminationDate: `${month}-28` })
          .its('status').should('equal', 200)
        cy.api('GET', `/api/commission/adjustments/${adjustment.id}`).then(({ body: cancelled }) => {
          expect(cancelled.status).to.equal('Cancelled')
          expect(money(cancelled.remaining)).to.equal(500)
          expect(cancelled.applications).to.have.length(2)
          cancelled.applications.forEach((app) => expect(app.status).to.equal('Cancelled'))
        })
      })
    })
  })
})
