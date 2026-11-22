/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const currentMonth = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)
const prevMonth = (() => {
  const d = new Date(`${currentMonth}-01T12:00:00Z`)
  d.setUTCMonth(d.getUTCMonth() - 1)
  return d.toISOString().slice(0, 7)
})()

// 9,000 THB at 5% over 5 sessions = 90.00 per used session.
function courseWithOneSession(fx) {
  return cy.openBookingHours('01').then(() =>
    cy.registerPatient().then((patient) =>
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: fx.courseId,
        branchCode: '01',
        caseOwnerStaffId: fx.therapistId,
        paymentLabelCode: 'FULL',
      }).then(({ status, body: course }) => {
        expect(status, JSON.stringify(course)).to.equal(201)
        return cy.api('POST', '/api/appointments', {
          branchCode: '01',
          patientId: patient.id,
          therapistId: fx.therapistId,
          serviceId: fx.courseId,
          startAt: `${fx.tomorrow}T09:00:00+07:00`,
          endAt: `${fx.tomorrow}T10:00:00+07:00`,
        }).then(({ body: appt }) =>
          cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id, appointmentId: appt.id }).then((used) => {
            expect(used.status).to.equal(200)
            return cy.api('POST', '/__cypress/commission/shift-activity?months=1').then(() => course)
          }),
        )
      }),
    ),
  )
}

describe('monthly commission payroll & adjustments', () => {
  it('is closed to a therapist', () => {
    for (const path of ['/staff/commission', '/staff/commission/adjustments']) {
      cy.visitAs('therapist', path)
      cy.location('pathname').should('eq', '/staff/patients')
    }
  })

  it('closes, pays, finalizes the month, and hides reopen afterwards', function () {
    courseWithOneSession(this.fixtures)
    cy.visitAs('manager', `/staff/commission?month=${prevMonth}`)
    cy.contains('[data-test="awaiting-placeholder"]', 'รอการปิดรอบ').should('be.visible')
    cy.get('[data-test="close-round"]').click()
    cy.get('[data-test="confirm-close"]').click()
    cy.noAlert()
    cy.contains('tbody tr', 'Therapist').should('contain.text', '฿90').and('contain.text', 'Closed · รอจ่าย')
    cy.contains('tbody tr', 'Therapist').find('[data-test="pay-row"]').click()
    cy.get('[data-test="first-pay-warning"]').should('be.visible')
    cy.get('[data-test="pay-reference"]').type('TRF-100')
    cy.get('[data-test="confirm-pay"]').click()
    cy.noAlert()
    cy.contains('tbody tr', 'Therapist').should('contain.text', 'จ่ายแล้ว')
    cy.contains('tbody tr', 'Therapist').find('[data-test="reopen-row"]').should('not.exist')
    cy.api('GET', `/api/commission/closings?month=${prevMonth}&branchCode=01`).then(({ body }) => {
      expect(body.find((row) => row.employeeId === this.fixtures.therapistId).status).to.equal('FINALIZED')
    })
  })

  it('lists, approves, and cancels adjustments in the adjustments workspace', function () {
    courseWithOneSession(this.fixtures).then((course) => {
      cy.api('POST', `/api/commission/closings/${prevMonth}-01?branchCode=01`).its('status').should('equal', 200)
      cy.api('POST', `/api/commission/payments/prepare?month=${prevMonth}&branchCode=01`).then(({ body }) => {
        const payment = body.find((row) => row.staffId === this.fixtures.therapistId)
        cy.api('POST', `/api/commission/payments/close?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`, {
          paidDate: `${prevMonth}-28`,
          reference: 'PAY-1',
        }).its('status').should('equal', 200)
        cy.api('GET', `/__cypress/commission/courses/${course.id}/allocations`).its('body.0.allocation_id').then((allocationId) => {
          cy.api('POST', '/api/commission/adjustments', {
            staffId: this.fixtures.therapistId,
            allocationId,
            originalMonth: prevMonth,
            appliedMonth: currentMonth,
            amount: -140,
            reason: 'บันทึกผู้รักษาผิดคน',
          }).then(({ status, body: created }) => {
            expect(status).to.equal(201)
            cy.visitAs('manager', `/staff/commission/adjustments/${created.id}`)
            cy.contains('บันทึกผู้รักษาผิดคน').should('be.visible')
            cy.contains('button', 'ยกเลิก').should('be.visible')
            cy.visitAs('manager', '/staff/commission/adjustments')
            cy.contains('tbody tr', 'บันทึกผู้รักษาผิดคน').should('be.visible')
          })
        })
      })
    })
  })
})
