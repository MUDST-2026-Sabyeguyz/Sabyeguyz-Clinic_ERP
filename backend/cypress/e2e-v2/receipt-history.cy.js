function sale(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode: '01',
    items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 800 }], ...overrides,
  }).then(response => { expect(response.status).to.equal(201); return response.body })
}
const history = (p, query = '') => cy.api('GET', `/api/payments?receiptsOnly=true&patientId=${p.id}${query}`)
const lines = fx => [
  { type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 },
  { type: 'SERVICE', referenceId: fx.serviceId, quantity: 1 },
]
describe('v2 patient receipt history', () => {
  it('filters interleaved patients and deduplicates receipts before server pagination', function () {
    cy.registerPatient().then(a => cy.registerPatient().then(b => {
      const ids = []
      for (let i = 0; i < 22; i++) {
        sale(this.fixtures, a, i === 10 ? { items: lines(this.fixtures), payments: [{ paymentMethodCode: 'Cash', amount: 2000 }] } : {}).then(created => ids.push(created.id))
        sale(this.fixtures, b)
      }
      cy.then(() => {
        history(a, '&page=0&size=20').then(({ body }) => {
          expect(body).to.include({ page: 0, size: 20, totalItems: 22, totalPages: 2 })
          expect(body.items.map(row => row.id)).to.deep.equal(ids.slice().reverse().slice(0, 20))
          expect(body.items.every(row => row.patient.id === a.id)).to.equal(true)
          expect(new Set(body.items.map(row => row.receiptId)).size).to.equal(20)
        })
        history(a, '&page=1&size=20').then(({ body }) => {
          expect(body.items.map(row => row.id)).to.deep.equal(ids.slice(0, 2).reverse())
          expect(body.totalItems).to.equal(22)
        })
        history(a, '&page=2&size=20').its('body.items').should('have.length', 0)
        history(a, '&branchCode=02').its('body.totalItems').should('equal', 0)
      })
    }))
  })
  it('preserves default transaction-line paging while receipt mode returns whole net', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, {
      items: lines(this.fixtures), billDiscountType: 'BAHT', billDiscountValue: 100,
      payments: [{ paymentMethodCode: 'Cash', amount: 500 }],
    }).then(created => {
      history(patient, '&size=1').then(({ body }) => {
        expect(body.totalItems).to.equal(1)
        expect(body.items[0]).to.include({ id: created.id, total: 1900, netAmount: 1900, paid: 500 })
      })
      cy.api('GET', `/api/payments?patientId=${patient.id}`).then(({ body }) => {
        expect(body.totalItems).to.equal(2)
        expect(body.items.map(row => row.total).sort((a, b) => a - b)).to.deep.equal([760, 1140])
      })
    }))
  })
  it('reads persisted receipt totals separately from partial payment through either legacy line id', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, {
      items: [{ ...lines(this.fixtures)[0], discountType: 'BAHT', discountValue: 100 }, lines(this.fixtures)[1]],
      billDiscountType: 'BAHT', billDiscountValue: 100, payments: [{ paymentMethodCode: 'QR', amount: 500 }],
    }).then(created => {
      const expected = { receiptId: created.receiptId, subtotalBeforeDiscount: 1900, billDiscountAmount: 100,
        taxableAmount: 1800, taxAmount: 0, netAmount: 1800, total: 1800, paid: 500, paymentLabel: 'Installment' }
      expect(created.receiptId).to.be.a('number')
      expect(created).to.include(expected)
      cy.api('GET', `/api/receipts/${created.id}`).then(({ body }) => {
        expect(body).to.include(expected)
        expect(body.items).to.have.length(2)
        expect(body.payments[0].paymentMethodCode).to.equal('PromptPay')
        cy.api('GET', `/api/receipts/${body.items[1].id}`).its('body').should('include', expected)
      })
      sale(this.fixtures, patient).then(next => {
        expect(next.id).not.to.equal(next.receiptId)
        cy.api('GET', `/api/receipts/${next.id}`).its('body').should('include', {
          id: next.id, receiptId: next.receiptId, receiptNo: next.receiptNo, netAmount: 800,
        })
      })
    }))
  })
  it('reads and lists a persisted historical Assessment without permitting new Assessment sales', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient).then(created => {
      cy.api('POST', `/__cypress/payment-policy/${created.id}/historical-assessment`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.updated).to.equal(1)
      })
      cy.api('GET', `/api/receipts/${created.id}`).its('body.items.0.type').should('equal', 'Assessment')
      history(patient).its('body.items.0.id').should('equal', created.id)
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        items: [{ type: 'ASSESSMENT', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }], payments: [],
      }).its('status').should('equal', 400)
    }))
  })
  it('keeps the sold item name on a printed receipt after the catalogue name changes', function () {
    cy.registerPatient().then(patient => {
      cy.api('POST', '/api/services', { code: 'SNAPSHOT_TEST', name: 'Sold item snapshot', price: 800, defaultDurationMinutes: 30 })
        .then(({ status, body: service }) => {
        expect(status).to.equal(201)
        sale(this.fixtures, patient, { items: [{ type: 'SERVICE', referenceId: service.id, quantity: 1 }] }).then(created => {
          cy.api('PUT', `/api/services/${service.id}`, {
            code: service.code, name: 'Renamed after sale', defaultDurationMinutes: 30, price: service.price,
          }).its('status').should('equal', 200)
          cy.api('GET', `/api/receipts/${created.id}`).then(({ body }) => {
            expect(body.items[0].description).to.equal(service.name)
          })
        })
      })
    })
  })
  for (const query of ['page=-1', 'size=0', 'size=101']) {
    it(`rejects invalid pagination ${query}`, () => cy.api('GET', `/api/payments?receiptsOnly=true&${query}`).its('status').should('equal', 400))
  }
  it('keeps authenticated patient receipt reads and requires authentication', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient).then(created => {
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        cy.apiAs(auth.token, 'GET', `/api/payments?receiptsOnly=true&patientId=${patient.id}`).its('body.items.0.id').should('equal', created.id)
      })
      cy.apiAs(null, 'GET', `/api/payments?receiptsOnly=true&patientId=${patient.id}`).its('status').should('equal', 401)
    }))
  })
})
