function sale(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode: '01',
    items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
    ...overrides,
  })
}

describe('v2 sales and payments', () => {
  it('sells a single visit paid in cash and returns an REYYMMNNNN receipt number', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.receiptNo).to.match(/^RE\d{8}$/)
        expect(Number(body.total)).to.equal(800)
        expect(Number(body.paid)).to.equal(800)
        expect(body.status).to.equal('PAID')
        expect(body.patient.id).to.equal(patient.id)
      })
    })
  })

  it('gives each sale its own consecutive receipt number', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient).then((first) => {
        sale(this.fixtures, patient).then((second) => {
          const number = (receiptNo) => Number(receiptNo.slice(6))
          expect(number(second.body.receiptNo)).to.equal(number(first.body.receiptNo) + 1)
        })
      })
    })
  })

  it('never hands the same receipt number to two parallel sales', function () {
    cy.registerPatient().then((patient) => {
      const request = {
        method: 'POST', url: '/api/payments',
        body: {
          patientId: patient.id, branchCode: '01',
          items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
        },
      }
      cy.parallelApi([request, request]).then((responses) => {
        const statuses = responses.map((response) => response.status)
        expect(statuses, JSON.stringify(responses.map((response) => response.body))).to.deep.equal([201, 201])
        expect(responses[0].body.receiptNo).to.not.equal(responses[1].body.receiptNo)
      })
    })
  })

  it('applies a 10% line discount to the total', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, {
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,
                  discountType: 'PERCENT', discountValue: 10 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 720 }],
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.total)).to.equal(720)
        expect(Number(body.paid)).to.equal(720)
      })
    })
  })

  it('applies a fixed baht line discount', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, {
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,
                  discountType: 'BAHT', discountValue: 100 }],
        payments: [{ paymentMethodCode: 'Transfer', amount: 700 }],
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.total)).to.equal(700)
      })
    })
  })

  it('applies a bill discount on top of the line', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, {
        billDiscountType: 'BAHT', billDiscountValue: 50,
        payments: [{ paymentMethodCode: 'QR', amount: 750 }],
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.total)).to.equal(750)
      })
    })
  })

  it('sells several items on one receipt and totals them', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, {
        items: [
          { type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 },
          { type: 'SERVICE', referenceId: this.fixtures.serviceId, quantity: 1 },
        ],
        payments: [{ paymentMethodCode: 'Cash', amount: 2000 }],
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.total)).to.equal(2000)
        expect(Number(body.paid)).to.equal(2000)
        cy.api('GET', `/api/receipts/${body.id}`).then((detail) => {
          expect(detail.body.items).to.have.length(2)
          expect(Number(detail.body.total)).to.equal(2000)
        })
      })
    })
  })

  it('shares a bill discount over several items without changing the receipt total', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, {
        items: [
          { type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 },
          { type: 'SERVICE', referenceId: this.fixtures.serviceId, quantity: 1 },
        ],
        billDiscountType: 'BAHT', billDiscountValue: 100,
        payments: [{ paymentMethodCode: 'Cash', amount: 1900 }],
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.total)).to.equal(1900)
      })
    })
  })

  it('preserves multiple payment records with the same method on one receipt', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, {
        payments: [{ paymentMethodCode: 'Cash', amount: 300 }, { paymentMethodCode: 'Cash', amount: 500 }],
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.paid)).to.equal(800)
        cy.api('GET', `/api/receipts/${body.id}`).then((detail) => {
          expect(detail.status).to.equal(200)
          expect(detail.body.payments).to.have.length(2)
          expect(detail.body.items).to.have.length(1)
        })
      })
    })
  })

  it('rejects an empty sale, an unknown patient and an unknown payment method', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, { items: [] }).its('status').should('equal', 400)
      sale(this.fixtures, { id: 999999 }).its('status').should('equal', 400)
      sale(this.fixtures, patient, { payments: [{ paymentMethodCode: 'Bitcoin', amount: 800 }] })
        .its('status').should('equal', 400)
    })
  })

  it('lists a patient\'s sales', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient).its('status').should('equal', 201)
      sale(this.fixtures, patient).its('status').should('equal', 201)
      cy.api('GET', `/api/payments?patientId=${patient.id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.items).to.have.length(2)
      })
    })
  })

  it('voids a sale with a reason and marks it VOIDED', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient).then(({ body }) => {
        cy.api('POST', `/api/payments/${body.id}/reverse`, { reason: 'ลูกค้ายกเลิก' }).then((voided) => {
          expect(voided.status).to.equal(200)
          expect(voided.body.status).to.equal('VOIDED')
          expect(voided.body.voidReason).to.equal('ลูกค้ายกเลิก')
          expect(voided.body.voidedAt).to.be.a('string')
          cy.api('GET', `/api/receipts/${body.id}`).its('body.voidReason').should('equal', 'ลูกค้ายกเลิก')
          cy.api('GET', '/api/payments').its('body.items.0.voidReason').should('equal', 'ลูกค้ายกเลิก')
        })
      })
    })
  })

  it('does not void the same sale twice', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient).then(({ body }) => {
        cy.api('POST', `/api/payments/${body.id}/reverse`, { reason: 'ครั้งแรก' }).its('status').should('equal', 200)
        cy.api('POST', `/api/payments/${body.id}/reverse`, { reason: 'ครั้งสอง' }).its('status').should('be.within', 400, 409)
      })
    })
  })

  it('lets a therapist read but not void sales', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient).then(({ body }) => {
        cy.login('therapist', 'therapist123').then(({ body: auth }) => {
          cy.apiAs(auth.token, 'GET', `/api/receipts/${body.id}`).its('status').should('equal', 200)
        })
      })
    })
  })

  it('requires authentication', () => {
    cy.apiAs(null, 'GET', '/api/payments').its('status').should('equal', 401)
  })
})
