/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
function sell(fx, patient, overrides = {}) {
  return cy
    .api('POST', '/api/payments', {
      patientId: patient.id,
      branchCode: '01',
      paymentLabelCode: 'FULL',
      items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
      payments: [{ paymentMethodCode: 'PromptPay', amount: 800 }],
      ...overrides,
    })
    .then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      return body
    })
}

function confirmSale() {
  cy.get('button.service-menu-action--sale').click()
  cy.contains('h1', 'รายละเอียดการชำระเงิน').should('be.visible')
  cy.chooseDropdown('ประเภทการชำระ', 'รายครั้ง')
  cy.get('input.filter-dropdown__button[aria-label="ค้นหารายการ"]').click()
  cy.get('[role="listbox"][aria-label="ค้นหารายการ"] [role="option"]')
    .should('not.contain.text', 'Assessment')
    .contains('กายภาพบำบัดทั่วไป 30 นาที')
    .click()
  cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
  cy.get('.payment-options').contains('button', 'พร้อมเพย์').click()
  cy.contains('button.primary', 'ชำระเงิน').click()
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
        cy.get('.receipt-paper').should('contain.text', sale.receiptNo)
        cy.window().then((win) => cy.stub(win, 'print').as('print'))
        cy.contains('button', 'พิมพ์ใบเสร็จ').click()
        cy.get('@print').should('have.been.calledOnce')
        cy.reload()
        cy.get('.receipt-paper').should('contain.text', sale.receiptNo)
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
      cy.location('pathname').should('eq', '/staff/services/sale')
      cy.get('[role="alert"]').should('be.visible')
      cy.get('.receipt-paper').should('not.exist')
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
            payments: [{ paymentMethodCode: 'PromptPay', amount: 2000 }],
          }).then((sale) => expected.push(sale.receiptNo))
          sell(fx, b)
        }
        cy.then(() => {
          cy.intercept('GET', '**/api/payments?*').as('history')
          cy.visitAs('therapist', `/staff/patients/${a.id}/history?tab=payments`)
          cy.wait('@history')
          cy.get('tbody tr').should('have.length', 22)
          cy.get('tbody tr').each((row) => expect(expected).to.include(row.find('td').eq(1).text().trim()))
        })
      })
    })
  })

  it('opens the second receipt by transaction ID after a multi-line first receipt and prints persisted discount totals', function () {
    const fx = this.fixtures
    cy.registerPatient().then((patient) => {
      sell(fx, patient, {
        items: [
          { type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 },
          { type: 'SERVICE', referenceId: fx.serviceId, quantity: 1 },
        ],
        payments: [{ paymentMethodCode: 'PromptPay', amount: 2000 }],
      })
      sell(fx, patient, {
        billDiscountType: 'BAHT',
        billDiscountValue: 50,
        payments: [{ paymentMethodCode: 'PromptPay', amount: 750 }],
      }).then((sale) => {
        expect(sale.id).not.to.equal(sale.receiptId)
        cy.api('GET', `/api/receipts/${sale.id}`).then(({ body: detail }) => {
          expect(Number(detail.billDiscountAmount)).to.equal(50)
          expect(Number(detail.netAmount)).to.equal(750)
          cy.visitAs('therapist', `/staff/patients/${patient.id}/history?tab=payments`)
          cy.contains('tr', sale.receiptNo).contains('button', 'ดูใบเสร็จ').click()
          cy.location('pathname').should('eq', `/staff/patients/${patient.id}/receipts/${sale.id}`)
          cy.get('.receipt-paper')
            .should('contain.text', sale.receiptNo)
            .and('contain.text', 'พร้อมเพย์')
          cy.contains('.receipt-net', '750.00').should('be.visible')
          cy.contains('.receipt-amounts p', 'ยอดชำระ').should('contain.text', '750.00')
          cy.contains('.receipt-amounts p', 'ส่วนลดท้ายบิล').should('contain.text', '50.00')
          cy.reload()
          cy.get('.receipt-paper').should('contain.text', sale.receiptNo)
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
        cy.api('GET', `/api/receipts/${sale.id}`).its('body.items.0.type').should('equal', 'Assessment')
        cy.visitAs('therapist', `/staff/patients/${patient.id}/history?tab=payments`)
        cy.contains('tr', sale.receiptNo).contains('button', 'ดูใบเสร็จ').click()
        cy.get('.receipt-paper').should('contain.text', sale.receiptNo).and('contain.text', 'กายภาพบำบัดทั่วไป 30 นาที')
        cy.window().then((win) => cy.stub(win, 'print').as('print'))
        cy.contains('button', 'พิมพ์ใบเสร็จ').click()
        cy.get('@print').should('have.been.calledOnce')
        cy.reload()
        cy.get('.receipt-paper').should('contain.text', sale.receiptNo)
      }),
    )
  })
})
