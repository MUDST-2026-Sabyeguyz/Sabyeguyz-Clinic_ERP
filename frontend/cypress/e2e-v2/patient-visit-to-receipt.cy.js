/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai. */
const workday = (fx) => {
  const day = new Date(`${fx.tomorrow}T12:00:00Z`)
  while (day.getUTCDay() === 0 || day.getUTCDay() === 1) day.setUTCDate(day.getUTCDate() + 1)
  return day.toISOString().slice(0, 10)
}

describe('patient visit through receipt history', () => {
  it('signs in, registers, books, treats, sells and finds the persisted receipt', function () {
    cy.openBookingHours('01')
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('manager')
    cy.get('input[autocomplete="current-password"]').type('manager123')
    cy.get('button[type="submit"]').click()
    cy.location('pathname').should('eq', '/staff/select-branch')
    cy.get('button.branch-option').first().click()
    cy.location('pathname').should('eq', '/staff/dashboard')

    cy.visit('/staff/patients/new')
    cy.get('input[type=radio][value=ต่างชาติ]').check()
    cy.get('input[placeholder="ชื่อ"]').type('Alice')
    cy.get('input[placeholder="นามสกุล"]').type('Workflow')
    cy.get('button[type="submit"]').click()
    cy.get('[aria-labelledby="registration-confirmation-title"]').contains('button', /^ยืนยัน$/).click()
    cy.contains('ลงทะเบียนผู้ป่วยสำเร็จ').should('be.visible')
    cy.get('[aria-labelledby="registration-success-title"] button').click()
    cy.location('pathname').should('match', /^\/staff\/patients\/\d+$/)
    cy.api('GET', '/api/patients').then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      const patient = body.items[0]

      cy.visit('/staff/appointments')
      cy.contains('button', 'สร้างนัดหมาย').should('be.enabled').click()
      cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('Alice')
      cy.contains('.patient-search-results button', 'Alice Workflow').click()
      cy.api('GET', '/api/catalog/therapists').then(({ body: therapists }) => {
        const therapist = therapists.find((item) => item.id === this.fixtures.therapistId)
        cy.get('input.filter-dropdown__button[aria-label="เลือกนักกายภาพ"]').click()
        cy.get('[role="listbox"][aria-label="เลือกนักกายภาพ"] [role="option"]').contains(therapist.fullName).click()
      })
      cy.contains('label', 'วันที่').pickDate(workday(this.fixtures))
      cy.contains('button', 'ดูตารางเวลาว่าง').click()
      cy.get('.appointment-time-drawer').within(() => {
        cy.contains('.appointment-time-slots button', /^10:00/).click()
        cy.contains('button', 'ยืนยันเวลา').click()
      })
      cy.contains('.appointment-save button.primary', /^บันทึก/).click()
      cy.get('.appointment-confirm-modal button.primary').click()
      cy.contains('tr', 'Alice Workflow', { timeout: 15000 }).should('contain.text', '10:00')
      cy.api('GET', '/api/appointments').then(({ body: appts }) => {
        expect(appts.totalItems).to.equal(1)
        const appointment = appts.items[0]

        cy.visit(`/staff/services?patientId=${patient.id}&appointmentId=${appointment.id}`)
        cy.get('button.service-menu-action--sale').click()
        cy.contains('h1', 'รายละเอียดการชำระเงิน').should('be.visible')
        cy.chooseDropdown('ประเภทการชำระ', 'รายครั้ง')
        cy.get('input.filter-dropdown__button[aria-label="ค้นหารายการ"]').click()
        cy.get('[role="listbox"][aria-label="ค้นหารายการ"] [role="option"]').contains('กายภาพบำบัดทั่วไป 30 นาที').click()
        cy.chooseDropdown('นักกายภาพ', 'Therapist Demo')
        cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
        cy.get('.payment-options').contains('button', 'พร้อมเพย์').click()
        cy.contains('button.primary', 'ชำระเงิน').click()
        cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
        cy.api('GET', `/api/appointments/${appointment.id}`).its('body.status').should('equal', 'CONFIRMED')

        cy.api('GET', `/api/payments?patientId=${patient.id}&receiptsOnly=true`).then(({ body: payments }) => {
          expect(payments.totalItems).to.equal(1)
          const receipt = payments.items[0]
          expect(receipt.status).to.equal('PAID')
          cy.get('.receipt-paper').should('contain.text', receipt.receiptNo)
          cy.visit(`/staff/patients/${patient.id}/history?tab=payments`)
          cy.contains('tr', receipt.receiptNo).should('contain.text', '800.00')
            .contains('button', 'ดูใบเสร็จ').click()
          cy.get('.receipt-paper').should('contain.text', receipt.receiptNo)
        })
      })
    })
  })
})
