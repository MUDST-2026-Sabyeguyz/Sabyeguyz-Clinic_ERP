function paidSale(fx, patient) {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode: '01',
    paymentLabelCode: 'FULL',
    items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
  }).then(({ body: sale }) => cy.api('GET', `/api/receipts/${sale.id}`).then(({ body }) => body.payments[0]))
}

const refund = (payment, amount) => cy.api('POST', `/api/payments/${payment.id}/refunds`, {
  amount, paymentMethodCode: 'BankTransfer', reason: 'ลูกค้าไม่พอใจ',
})

describe('v2 refunds', () => {
  it('requests a refund as Pending and completes it', function () {
    cy.registerPatient().then((patient) => {
      paidSale(this.fixtures, patient).then((payment) => {
        refund(payment, 300).then(({ status, body }) => {
          expect(status).to.equal(201)
          expect(body.status).to.equal('Pending')
          expect(Number(body.amount)).to.equal(300)
          cy.api('POST', `/api/refunds/${body.id}/complete`).then((done) => {
            expect(done.status).to.equal(200)
            expect(done.body.status).to.equal('Completed')
            expect(done.body.refundedAt).to.be.a('string')
          })
        })
      })
    })
  })

  it('never lets refunds exceed the payment', function () {
    cy.registerPatient().then((patient) => {
      paidSale(this.fixtures, patient).then((payment) => {
        refund(payment, 500).its('status').should('equal', 201)
        refund(payment, 400).its('status').should('equal', 400)
        refund(payment, 300).its('status').should('equal', 201)
      })
    })
  })

  it('frees the amount again when a refund fails', function () {
    cy.registerPatient().then((patient) => {
      paidSale(this.fixtures, patient).then((payment) => {
        refund(payment, 800).then(({ body }) => {
          cy.api('POST', `/api/refunds/${body.id}/fail`).its('body.status').should('equal', 'Failed')
        })
        refund(payment, 800).its('status').should('equal', 201)
      })
    })
  })

  it('rejects a zero amount and an unknown payment', function () {
    cy.registerPatient().then((patient) => {
      paidSale(this.fixtures, patient).then((payment) => {
        refund(payment, 0).its('status').should('equal', 400)
        refund({ id: 999999 }, 100).its('status').should('equal', 400)
      })
    })
  })

  it('does not complete a refund twice and lists refunds per payment', function () {
    cy.registerPatient().then((patient) => {
      paidSale(this.fixtures, patient).then((payment) => {
        refund(payment, 100).then(({ body }) => {
          cy.api('POST', `/api/refunds/${body.id}/complete`).its('status').should('equal', 200)
          cy.api('POST', `/api/refunds/${body.id}/complete`).its('status').should('equal', 400)
        })
        cy.api('GET', `/api/payments/${payment.id}/refunds`).its('body').should('have.length', 1)
      })
    })
  })

  it('is manager-only', function () {
    cy.registerPatient().then((patient) => {
      paidSale(this.fixtures, patient).then((payment) => {
        cy.login('therapist', 'therapist123').then(({ body: auth }) => {
          cy.apiAs(auth.token, 'POST', `/api/payments/${payment.id}/refunds`,
            { amount: 100, paymentMethodCode: 'BankTransfer', reason: 'x' }).its('status').should('equal', 403)
        })
      })
    })
  })
})
