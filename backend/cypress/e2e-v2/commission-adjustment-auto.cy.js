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
    code: 'AUTO-ADJ', name: 'คอร์สทดสอบปรับยอด', price: 10000, sessions: 10,
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

describe('automatic adjustment from a paid source', () => {
  it('includes an addition without a second approval and records the paid month', function () {
    paidSource(this.fixtures.therapistId).then((allocationId) => {
      cy.api('POST', '/api/commission/adjustments', {
        staffId: this.fixtures.therapistId, branchCode: '02', allocationId,
        originalMonth: month, appliedMonth: nextMonth, amount: 50,
        reason: 'อ้างอิงคนละสาขา',
      }).its('status').should('equal', 400)
      cy.api('POST', '/api/commission/adjustments', {
        staffId: this.fixtures.therapistId, branchCode: '01', allocationId,
        originalMonth: month, appliedMonth: nextMonth, amount: 50, reason: 'แก้ยอดที่จ่ายแล้ว',
      }).then(({ status, body: adjustment }) => {
        expect(status).to.equal(201)
        expect(adjustment.status).to.equal('Approved')
        expect(adjustment.automatic).to.equal(true)
        prepare(nextMonth).then(({ body: payments }) => {
          const payment = payments.find((row) => row.staffId === this.fixtures.therapistId)
          expect(money(payment.adjustment)).to.equal(50)
          close(nextMonth)
          cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 200)
          cy.api('GET', `/api/commission/adjustments/${adjustment.id}`).then(({ body: done }) => {
            expect(done.status).to.equal('Applied')
            expect(money(done.remaining)).to.equal(0)
            expect(done.applications[0].month).to.equal(nextMonth)
          })
        })
      })
    })
  })

  it('carries an unaffordable deduction and cancels its balance on resignation', function () {
    paidSource(this.fixtures.therapistId).then((allocationId) => {
      cy.api('POST', '/api/commission/adjustments', {
        staffId: this.fixtures.therapistId, branchCode: '01', allocationId,
        originalMonth: month, appliedMonth: nextMonth, amount: -500,
        reason: 'แก้ยอดที่จ่ายเกิน', approvalReference: 'AGREEMENT-1',
      }).then(({ body: adjustment }) => {
        prepare(nextMonth).then(({ body: payments }) => {
          const payment = payments.find((row) => row.staffId === this.fixtures.therapistId)
          expect(money(payment.adjustment)).to.equal(0)
          close(nextMonth)
          cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 200)
          cy.api('GET', `/api/commission/adjustments/${adjustment.id}`).then(({ body: remaining }) => {
            expect(money(remaining.remaining)).to.equal(500)
          })
          prepare(following(nextMonth)).then(({ body: later }) => {
            expect(later.find((row) => row.staffId === this.fixtures.therapistId).applications)
              .to.have.length(1)
          })
          cy.api('POST', `/api/staff/${this.fixtures.therapistId}/terminate`, {
            terminationDate: `${month}-28`,
          }).its('status').should('equal', 200)
          cy.api('GET', `/api/commission/adjustments/${adjustment.id}`).then(({ body: cancelled }) => {
            expect(cancelled.status).to.equal('Cancelled')
            expect(cancelled.cancellationReason).to.equal('นักกายภาพลาออก')
            expect(money(cancelled.remaining)).to.equal(500)
          })
        })
      })
    })
  })
})
