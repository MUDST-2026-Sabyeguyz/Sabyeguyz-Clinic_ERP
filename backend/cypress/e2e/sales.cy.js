import { saleBody } from '../support/commands'

describe('Payments and receipts API', () => {
  beforeEach(function () {
    cy.registerPatient().then((patient) => {
      cy.wrap(saleBody(this.fixtures.courseId, patient.id)).as('saleBody')
    })
  })

  function sell(body) {
    return cy.api('POST', '/api/payments', body).then(({ status, body: sale }) => {
      expect(status).to.equal(201)
      return sale
    })
  }

  it('issues a paid receipt with Location and its course purchase', function () {
    cy.api('POST', '/api/payments', this.saleBody).then(({ status, headers, body }) => {
      expect(status).to.equal(201)
      expect(headers.location).to.include(`/api/receipts/${body.id}`)
      expect(body.receiptNo).to.match(/^RC\d{10}$/)
      expect(body).to.include({ total: 9000, paid: 9000, status: 'PAID' })
      expect(body.patient.hn).not.to.be.empty
      expect(body.items[0].coursePurchaseId).to.be.a('number')
    })
  })

  it('rejects a receipt with no items', function () {
    cy.api('POST', '/api/payments', { ...this.saleBody, items: [] }).then(({ status, body }) => {
      expect(status).to.equal(400)
      expect(body.fields).to.have.property('items')
    })
  })

  it('rejects payments that do not match the total', function () {
    cy.api('POST', '/api/payments', { ...this.saleBody, payments: [{ paymentMethodCode: 'CASH', amount: 8000 }] })
      .then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.message).to.include('ไม่ตรงกับยอดที่ต้องชำระ')
      })
  })

  it('returns 404 for an unknown patient', function () {
    cy.api('POST', '/api/payments', { ...this.saleBody, patientId: 999999 }).its('status').should('equal', 404)
  })

  it('returns receipt items and payment details', function () {
    sell(this.saleBody).then(({ id }) => {
      cy.api('GET', `/api/receipts/${id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.items).to.have.length(1)
        expect(body.items[0].type).to.equal('COURSE')
        expect(body.payments).to.have.length(1)
        expect(body.payments[0]).to.include({ type: 'PAYMENT', paymentMethodCode: 'CASH' })
      })
    })
  })

  it('voids a receipt and nets the revenue to zero', function () {
    sell(this.saleBody).then(({ id }) => {
      cy.api('POST', `/api/payments/${id}/reverse`, { reason: 'ลูกค้าขอยกเลิก' }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.include({ status: 'VOIDED', paid: 0, voidReason: 'ลูกค้าขอยกเลิก' })
        expect(body.payments).to.have.length(2)
        expect(body.payments[1]).to.include({ type: 'REFUND', amount: -9000 })
      })
    })
  })

  it('rejects voiding without a reason', function () {
    sell(this.saleBody).then(({ id }) => {
      cy.api('POST', `/api/payments/${id}/reverse`, { reason: '   ' }).then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.fields).to.have.property('reason')
      })
    })
  })

  it('returns 409 when voiding the same receipt twice', function () {
    sell(this.saleBody).then(({ id }) => {
      cy.api('POST', `/api/payments/${id}/reverse`, { reason: 'ครั้งแรก' }).its('status').should('equal', 200)
      cy.api('POST', `/api/payments/${id}/reverse`, { reason: 'ครั้งที่สอง' }).then(({ status, body }) => {
        expect(status).to.equal(409)
        expect(body.message).to.be.a('string').and.not.be.empty
      })
    })
  })

  it('returns 404 for an unknown receipt', () => {
    cy.api('GET', '/api/receipts/999999').its('status').should('equal', 404)
  })

  it('lists patient receipts without detailed items', function () {
    sell(this.saleBody)
    cy.api('GET', '/api/payments', undefined, { qs: { patientId: this.saleBody.patientId } }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).to.include({ totalItems: 1, page: 0 })
      expect(body.items).to.have.length(1)
      expect(body.items[0].receiptNo).to.match(/^RC\d{10}$/)
      expect(body.items[0].paid).to.equal(9000)
      expect(body.items[0]).not.to.have.property('items')
    })
  })

  it('rejects an oversized receipt page', () => {
    cy.api('GET', '/api/payments', undefined, { qs: { size: 500 } }).its('status').should('equal', 400)
  })

  it('shows the sold course on the patient course list', function () {
    sell(this.saleBody)
    cy.api('GET', `/api/patients/${this.saleBody.patientId}/courses`).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).to.have.length(1)
      expect(body[0]).to.include({ remaining: 6, usable: true })
    })
  })
})
