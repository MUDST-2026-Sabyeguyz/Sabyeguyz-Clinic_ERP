/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const currentMonth = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' })
  .format(new Date()).slice(0, 7)
const prevMonth = (() => {
  const d = new Date(`${currentMonth}-01T12:00:00Z`)
  d.setUTCMonth(d.getUTCMonth() - 1)
  return d.toISOString().slice(0, 7)
})()

function courseWithOneSession(fx) {
  return cy.openBookingHours('01').then(() =>
    cy.registerPatient().then((patient) =>
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: fx.courseId,
        branchCode: '01',
        caseOwnerStaffId: fx.therapistId,
        paymentLabelCode: 'FULL',
      }).then(({ body: course }) =>
        cy.api('POST', '/api/appointments', {
          branchCode: '01',
          patientId: patient.id,
          therapistId: fx.therapistId,
          serviceId: fx.courseId,
          startAt: `${fx.tomorrow}T09:00:00+07:00`,
          endAt: `${fx.tomorrow}T10:00:00+07:00`,
        }).then(({ body: appt }) =>
          cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id, appointmentId: appt.id })
            .its('status').should('equal', 200)
            .then(() => cy.api('POST', '/__cypress/commission/shift-activity?months=1').its('status').should('equal', 200)),
        ),
      ),
    ),
  )
}

describe('sale owner and therapist compensation', () => {
  it('credits a course sale from a therapist login to that therapist', function () {
    cy.registerPatient().then((patient) => {
      cy.visitAs('therapist', `/staff/services?patientId=${patient.id}`)
      cy.get('button.service-menu-action--sale').click()
      cy.contains('h1', 'รายละเอียดการชำระเงิน').should('be.visible')
      cy.chooseDropdown('ประเภทการชำระ', 'รายคอร์ส')
      cy.get('input.filter-dropdown__button[aria-label="ค้นหารายการ"]').click()
      cy.get('[role="listbox"][aria-label="ค้นหารายการ"] [role="option"]').contains('คอร์สทดสอบ API').click()
      cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
      cy.get('.payment-options').contains('button', 'พร้อมเพย์').click()
      cy.contains('button.primary', 'ชำระเงิน').click()
      cy.noAlert()
      cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
      cy.api('GET', `/api/commission/closings?month=${currentMonth}&branchCode=01`).then(({ body: closings }) => {
        expect(closings.map((row) => row.employeeId)).to.include(this.fixtures.therapistId)
      })
    })
  })

  it('says there is no payment yet when no activity exists', () => {
    cy.visitAs('therapist', `/staff/compensation?month=${prevMonth}`)
    cy.contains('ยังไม่มีรายการค่าตอบแทนในเดือนนี้').should('be.visible')
    cy.get('[aria-label="สรุปค่าตอบแทน"]').should('contain.text', '฿0.00')
  })

  it('shows waiting before closing, then calculates ฿90.00 after closing & payment round close', function () {
    courseWithOneSession(this.fixtures)
    cy.api('POST', `/api/commission/payments/prepare?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
    cy.visitAs('therapist', `/staff/compensation?month=${prevMonth}`)
    cy.get('[aria-label="สรุปค่าตอบแทน"]').should('contain.text', 'รอปิดยอด').and('contain.text', 'รอคำนวณ')
    cy.api('POST', `/api/commission/closings/${prevMonth}-01?branchCode=01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/prepare?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/close?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
    cy.reload()
    cy.get('[aria-label="สรุปค่าตอบแทน"]').should('contain.text', '฿90.00').and('not.contain.text', 'รอปิดยอด')
  })

  it('lists paid months in payment history only after payment is marked paid', function () {
    courseWithOneSession(this.fixtures)
    cy.api('POST', `/api/commission/closings/${prevMonth}-01?branchCode=01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/prepare?month=${prevMonth}&branchCode=01`).then(({ body }) => {
      const mine = body.find((row) => row.staffId === this.fixtures.therapistId)
      cy.visitAs('therapist', `/staff/compensation/payments?month=${prevMonth}`)
      cy.contains('ยังไม่มีงวดที่จ่ายแล้ว').should('be.visible')
      cy.api('POST', `/api/commission/payments/close?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
      cy.api('POST', `/api/commission/payments/${mine.id}/pay`, {
        paidDate: `${prevMonth}-15`,
        reference: 'PT-SELF-1',
      }).its('status').should('equal', 200)
      cy.reload()
      cy.get('.therapist-payment-row').should('have.length', 1).and('contain.text', '฿90.00').and('contain.text', 'Paid')
    })
  })

  it('opens the formula page read-only and drills into line detail and source', function () {
    courseWithOneSession(this.fixtures)
    cy.api('POST', `/api/commission/closings/${prevMonth}-01?branchCode=01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/prepare?month=${prevMonth}&branchCode=01`).its('status').should('equal', 200)
    cy.visitAs('therapist', `/staff/compensation?month=${prevMonth}`)
    cy.contains('h2', 'หลักการคำนวณ').should('be.visible')
    cy.contains('a', 'ดูรายละเอียดวิธีคำนวณ').click()
    cy.location('pathname').should('eq', '/staff/commission/formula')
    cy.contains('h1', 'สูตรคำนวณค่าตอบแทน').should('be.visible')
    cy.get('input, button[type="submit"]').should('not.exist')
    cy.visitAs('therapist', `/staff/compensation?month=${prevMonth}&tab=commission`)
    cy.get('a.therapist-line-row').should('have.length', 1).and('contain', '฿90.00').click()
    cy.contains('h2', 'รายละเอียดค่าคอมมิชชัน').should('be.visible')
    cy.contains('a', 'ดูรายการขายต้นทาง').click()
    cy.contains('h1', 'รายละเอียดรายการขายต้นทาง').should('be.visible')
    cy.contains('a', 'กลับรายละเอียดค่าคอมมิชชัน').click()
    cy.contains('h2', 'รายละเอียดค่าคอมมิชชัน').should('be.visible')
  })
})
