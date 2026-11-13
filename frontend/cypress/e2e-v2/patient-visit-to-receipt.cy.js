/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai. */
describe('patient visit through receipt history', () => {
  it('signs in, registers, books, treats, sells and finds the persisted receipt', function () {
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('manager')
    cy.get('input[autocomplete="current-password"]').type('manager123')
    cy.get('button[type="submit"]').click()
    cy.location('pathname').should('eq', '/staff/dashboard')

    cy.visit('/staff/patients/new')
    cy.get('input[type=radio][value=ต่างชาติ]').check()
    cy.get('input[placeholder="ชื่อ"]').type('Alice')
    cy.get('input[placeholder="นามสกุล"]').type('Workflow')
    cy.contains('button', 'บันทึก').click()
    cy.get('.confirmation-modal').contains('button', /^ใช่$/).click()
    cy.contains('ลงทะเบียนผู้ป่วยสำเร็จ').should('be.visible')
    cy.contains('button', 'เปิดข้อมูลผู้ป่วย').click()
    cy.location('pathname').should('match', /^\/staff\/patients\/\d+$/)
    cy.api('GET', '/api/patients').then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      const patient = body.items[0]

      cy.visit('/staff/appointments')
      cy.contains('button', 'สร้างนัดหมาย').should('be.enabled').click()
      cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('Alice')
      cy.contains('.patient-search-results button', 'Alice Workflow').click()
      cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
      cy.chooseDropdown('เลือกเวลา', '10:30')
      cy.contains('.appointment-save button', 'บันทึกนัดหมาย').click()
      cy.get('.appointment-confirm-modal').contains('button', /^ใช่$/).click()
      cy.contains('tr', 'Alice Workflow', { timeout: 15000 }).should('contain.text', '10:30')
      cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 1)

      for (const [status, action] of [
        ['ARRIVED', 'check-in'],
        ['IN_SERVICE', 'start-service'],
        ['COMPLETED', 'complete'],
      ]) {
        cy.intercept('POST', `**/api/appointments/*/${action}`).as('statusChange')
        cy.contains('tr', 'Alice Workflow').find('select.status-select').select(status)
        cy.wait('@statusChange').its('response.statusCode').should('equal', 200)
        cy.contains('.status-feedback', 'บันทึกแล้ว').should('be.visible')
      }
      cy.api('GET', '/api/appointments').its('body.items.0.status').should('equal', 'COMPLETED')

      cy.visit(`/staff/services?patientId=${patient.id}`)
      cy.contains('button', 'เริ่มขายบริการ').click()
      cy.contains('.sale-tabs button', 'Single Visit').click()
      cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
      cy.contains('button', 'ยืนยันรับชำระเงิน').click()
      cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
      cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
      cy.api('GET', `/api/payments?patientId=${patient.id}&receiptsOnly=true`).then(({ body: payments }) => {
        expect(payments.totalItems).to.equal(1)
        const receipt = payments.items[0]
        expect(receipt.status).to.equal('PAID')
        cy.get('.printable-receipt').should('contain.text', receipt.receiptNo)
        cy.contains('a', 'ประวัติใบเสร็จ').click()
        cy.location('pathname').should('eq', `/staff/patients/${patient.id}/history`)
        cy.contains('tr', receipt.receiptNo).should('contain.text', '฿800.00')
          .contains('a', 'ดูใบเสร็จ').click()
        cy.get('.printable-receipt').should('contain.text', receipt.receiptNo)
      })
    })
  })
})
