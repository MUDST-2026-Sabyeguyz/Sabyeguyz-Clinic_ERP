/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
describe('P4 persisted payment labels and method display', () => {
  for (const [labelCode, amount, label] of [
    ['DEPOSIT', 0, 'มัดจำ'],
    ['INSTALLMENT', 9000, 'ผ่อน'],
    ['FULL', 9000, 'ชำระเต็ม'],
  ]) {
    it(`displays persisted label ${label} without fake credit balances`, function () {
      cy.registerPatient().then((patient) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          salespersonStaffId: this.fixtures.managerStaffId,
          paymentLabelCode: labelCode,
          items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
          payments: amount ? [{ paymentMethodCode: 'PromptPay', amount }] : [],
        }).then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(201)
          cy.visitAs('therapist', `/staff/patients/${patient.id}/history?tab=payments`)
          cy.contains('tr', body.receiptNo).contains('button', 'ดูใบเสร็จ').click()
          cy.get('.receipt-paper').should('be.visible').and('contain.text', label)
          if (amount) cy.get('.receipt-paper').should('contain.text', 'พร้อมเพย์')
          cy.get('.receipt-paper').should('contain.text', 'แต้มคงเหลือ: —').and('contain.text', 'วงเงินคงเหลือ: —')
        })
      })
    })
  }
})
