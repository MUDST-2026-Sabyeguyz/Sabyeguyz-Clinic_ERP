/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
function sell(fx, patient, overrides = {}) {
  return cy
    .api('POST', '/api/payments', {
      patientId: patient.id,
      branchCode: '01',
      items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
      payments: [{ paymentMethodCode: 'QR', amount: 100 }],
      ...overrides,
    })
    .then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      return body
    })
}
function confirmSale() {
  cy.contains('button', 'เริ่มขายบริการ').click()
  cy.get('.sale-tabs').should('not.contain.text', 'Assessment')
  cy.get('select[aria-label="รายการบริการ"] option').should('not.contain.text', 'Assessment')
  cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
  cy.contains('button', 'ยืนยันรับชำระเงิน').click()
  cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
}
describe('P5 patient receipt history and persisted printing', () => {
  it('opens the paid document automatically and reloads with no extra receipt', () => {
    cy.registerPatient().then((patient) => {
      cy.intercept('POST', '**/api/payments').as('sale')
      cy.visitAs('therapist', `/staff/services?patientId=${patient.id}`)
      confirmSale()
      cy.wait('@sale').then(({ response }) => {
        expect(response.statusCode).to.equal(201)
        const sale = response.body
        cy.location('pathname').should('eq', `/staff/receipts/${sale.id}`)
        cy.get('.printable-receipt').should('contain.text', sale.receiptNo)
        cy.window().then((win) => cy.stub(win, 'print').as('print'))
        cy.get('[data-test="print"]').click()
        cy.get('@print').should('have.been.calledOnce')
        cy.reload()
        cy.get('.printable-receipt').should('contain.text', sale.receiptNo)
        cy.api('GET', `/api/payments?patientId=${patient.id}&receiptsOnly=true`)
          .its('body.totalItems')
          .should('equal', 1)
      })
    })
  })
  it('keeps a rejected payment on the form and never opens print', () => {
    cy.registerPatient().then((patient) => {
      cy.intercept('POST', '**/api/payments', {
        statusCode: 400,
        body: { message: 'Rejected test payment' },
      }).as('failedSale')
      cy.visitAs('therapist', `/staff/services?patientId=${patient.id}`)
      cy.window().then((win) => cy.stub(win, 'print').as('print'))
      confirmSale()
      cy.wait('@failedSale')
      cy.location('pathname').should('eq', '/staff/services')
      cy.get('[role="alert"]').should('be.visible')
      cy.get('.printable-receipt').should('not.exist')
      cy.get('@print').should('not.have.been.called')
      cy.api('GET', `/api/payments?patientId=${patient.id}&receiptsOnly=true`)
        .its('body.totalItems')
        .should('equal', 0)
    })
  })
  it('pages more than 20 receipts for A without B and deduplicates multi-line documents', function () {
    const fx = this.fixtures
    cy.registerPatient({ firstName: 'PatientA' }).then((a) => {
      cy.registerPatient({ firstName: 'PatientB' }).then((b) => {
        const expected = []
        for (let index = 0; index < 22; index++) {
          sell(fx, a, {
            items: [
              { type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 },
              { type: 'SERVICE', referenceId: fx.serviceId, quantity: 1 },
            ],
          }).then((sale) => expected.push(sale.receiptNo))
          sell(fx, b)
        }
        cy.then(() => {
          cy.intercept('GET', '**/api/payments?*').as('history')
          cy.visitAs('therapist', `/staff/patients/${a.id}/history`)
          cy.wait('@history')
            .its('request.url')
            .should('include', 'receiptsOnly=true')
            .and('include', `patientId=${a.id}`)
            .and('include', 'size=20')
          cy.get('tbody tr').should('have.length', 20)
          cy.get('tbody tr').each((row) => expect(expected).to.include(row.find('td').eq(1).text()))
          cy.contains('เฉพาะใบเสร็จในหน้านี้').should('be.visible')
          cy.contains('button', 'ถัดไป').click()
          cy.get('tbody tr').should('have.length', 2)
          cy.get('tbody tr').each((row) => expect(expected).to.include(row.find('td').eq(1).text()))
          cy.contains('button', 'ก่อนหน้า').click()
          cy.get('tbody tr').should('have.length', 20)
        })
      })
    })
  })
  it('opens the second receipt by transaction ID after a multi-line first receipt and prints persisted partial-payment totals', function () {
    const fx = this.fixtures
    cy.registerPatient().then((patient) => {
      sell(fx, patient, {
        items: [
          { type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 },
          { type: 'SERVICE', referenceId: fx.serviceId, quantity: 1 },
        ],
      })
      sell(fx, patient, { billDiscountType: 'BAHT', billDiscountValue: 50 }).then((sale) => {
        expect(sale.id).not.to.equal(sale.receiptId)
        cy.api('GET', `/api/receipts/${sale.id}`).then(({ body: detail }) => {
          expect(detail.netAmount).not.to.equal(detail.paid)
          cy.visitAs('therapist', `/staff/patients/${patient.id}/history`)
          cy.contains('tr', sale.receiptNo).contains('a', 'ดูใบเสร็จ').click()
          cy.location('pathname').should('eq', `/staff/receipts/${sale.id}`)
          cy.get('.printable-receipt')
            .should('contain.text', sale.receiptNo)
            .and('contain.text', 'พร้อมเพย์')
          cy.get('[data-test="net"]').should(
            'contain.text',
            Number(detail.netAmount).toLocaleString('th-TH', { minimumFractionDigits: 2 }),
          )
          cy.get('[data-test="paid"]').should('contain.text', '100.00')
          cy.get('[data-test="discount"]').should('contain.text', '50.00')
          cy.get('[data-test="tax"]').should('contain.text', '0.00')
          cy.reload()
          cy.get('.printable-receipt').should('contain.text', sale.receiptNo)
          cy.api('GET', `/api/payments?patientId=${patient.id}&receiptsOnly=true`)
            .its('body.totalItems')
            .should('equal', 2)
        })
      })
    })
  })
  it('still reads and prints persisted historical Assessment from patient history', function () {
    cy.registerPatient().then((patient) =>
      sell(this.fixtures, patient).then((sale) => {
        cy.api('POST', `/__cypress/payment-policy/${sale.id}/historical-assessment`).then(
          ({ status, body }) => {
            expect(status).to.equal(200)
            expect(body.updated).to.equal(1)
          },
        )
        cy.visitAs('therapist', `/staff/patients/${patient.id}/history`)
        cy.contains('tr', sale.receiptNo).contains('a', 'ดูใบเสร็จ').click()
        cy.get('.printable-receipt').should('contain.text', 'Assessment')
        cy.window().then((win) => cy.stub(win, 'print').as('print'))
        cy.get('[data-test="print"]').click()
        cy.get('@print').should('have.been.calledOnce')
        cy.reload()
        cy.get('.printable-receipt').should('contain.text', 'Assessment')
      }),
    )
  })
})
