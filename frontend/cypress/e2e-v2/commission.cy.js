/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const currentMonth = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)
const prevMonth = (() => {
  const d = new Date(`${currentMonth}-01T12:00:00Z`)
  d.setUTCMonth(d.getUTCMonth() - 1)
  return d.toISOString().slice(0, 7)
})()

function sellCourse(fx, patient) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id,
    courseId: fx.courseId,
    branchCode: '01',
    caseOwnerStaffId: fx.therapistId,
    paymentLabelCode: 'FULL',
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
}

describe('commission overview and closing', () => {
  it('are closed to a therapist', () => {
    for (const path of ['/staff/commission', '/staff/settings/commission-tiers', '/staff/commission/adjustments']) {
      cy.visitAs('therapist', path)
      cy.location('pathname').should('eq', '/staff/patients')
    }
  })

  it('shows empty state when no compensation data exists for the branch', () => {
    cy.visitAs('manager', `/staff/commission?month=${prevMonth}`)
    cy.contains('h1', 'ภาพรวมค่าตอบแทน').should('be.visible')
    cy.contains('ยังไม่มีข้อมูลค่าตอบแทน').should('be.visible')
  })

  it('closes the previous month and locks courses with tier rate calculation', function () {
    cy.registerPatient().then((patient) => sellCourse(this.fixtures, patient))
    cy.api('POST', '/__cypress/commission/shift-activity?months=1').its('status').should('equal', 200)
    cy.visitAs('manager', `/staff/commission?month=${prevMonth}`)
    cy.contains('tbody tr', '9,000').should('contain.text', '5%').and('contain.text', 'ยังไม่มีงวดจ่าย')
    cy.get('[data-test="close-round"]').click()
    cy.contains('h2', 'ยืนยันปิดรอบ').should('be.visible')
    cy.get('[data-test="confirm-close"]').click()
    cy.noAlert()
    cy.get('[data-test="closing-history"]').should('contain.text', 'Closed · รอจ่าย')
    cy.api('GET', '/api/patients').its('body.items.0.id')
      .then((id) => cy.api('GET', `/api/patients/${id}/courses`))
      .its('body.0.commissionStatus').should('equal', 'LOCKED')
  })

  it('reopens a closed month when a reason is provided', function () {
    cy.openBookingHours('01')
    cy.registerPatient().then((patient) =>
      sellCourse(this.fixtures, patient).then((course) =>
        cy.api('POST', '/api/appointments', {
          branchCode: '01',
          patientId: patient.id,
          therapistId: this.fixtures.therapistId,
          serviceId: this.fixtures.courseId,
          startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`,
          endAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
        }).then(({ body: appt }) =>
          cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id, appointmentId: appt.id }).its('status').should('equal', 200),
        ),
      ),
    )
    cy.api('POST', '/__cypress/commission/shift-activity?months=1').its('status').should('equal', 200)
    cy.api('POST', `/api/commission/closings/${prevMonth}?branchCode=01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/prepare?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/close?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
    cy.visitAs('manager', `/staff/commission?month=${prevMonth}`)
    cy.get('[data-test="reopen-row"]').first().click()
    cy.get('[data-test="reopen-reason"]').type('ขายคอร์สเพิ่มก่อนจ่าย')
    cy.get('[data-test="confirm-reopen"]').click()
    cy.noAlert()
    cy.contains('tbody tr', '9,000').should('contain.text', 'Open · ยังไม่ปิด')
  })
})
