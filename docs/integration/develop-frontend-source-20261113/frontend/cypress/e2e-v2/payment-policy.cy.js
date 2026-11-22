/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
describe('P4 persisted payment labels and method display', () => {
  for (const [amount, label] of [[0, 'มัดจำ'], [100, 'ผ่อน'], [800, 'ชำระเต็ม']]) {
    it(`displays persisted label ${label} without fake credit balances`, function () {
      cy.registerPatient().then((patient) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id, branchCode: '01',
          items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
          payments: amount ? [{ paymentMethodCode: 'QR', amount }] : [],
        }).then(({ status, body }) => {
          expect(status).to.equal(201)
          cy.visitAs('therapist', `/staff/patients/${patient.id}/history`)
          cy.contains('tr', body.receiptNo).contains('a', 'ดูใบเสร็จ').click()
          cy.get('.printable-receipt').should('be.visible').and('contain.text', label)
          if (amount) cy.get('.printable-receipt').should('contain.text', 'พร้อมเพย์')
          cy.get('.printable-receipt').should('not.contain.text', 'แต้มคงเหลือ').and('not.contain.text', 'วงเงินคงเหลือ')
        })
      })
    })
  }
})
