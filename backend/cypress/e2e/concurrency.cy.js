import { appointmentBody, saleBody } from '../support/commands'

const post = (url, body) => ({ method: 'POST', url, body })
const patch = (url, body) => ({ method: 'PATCH', url, body })
const successful = (responses) => responses.filter(({ status }) => status >= 200 && status < 300)
const expectOneWinner = (responses, success = 200) => {
  expect(responses.map(({ status }) => status).sort()).to.deep.equal([success, 409])
}

function create(method, url, body, expected = 201) {
  return cy.api(method, url, body).then(({ status, body: result }) => {
    expect(status).to.equal(expected)
    return result
  })
}

describe('AppointmentConcurrencyTest via concurrent HTTP transactions', () => {
  beforeEach(function () {
    cy.registerPatient().then((patient) => cy.wrap(appointmentBody(this.fixtures, patient.id)).as('booking'))
  })

  it('allowsOnlyOneBookingWhenTwoRequestsRaceForTheSameSlot', function () {
    cy.parallelApi([post('/api/appointments', this.booking), post('/api/appointments', this.booking)])
      .then((responses) => expectOneWinner(responses, 201))
    cy.api('GET', '/api/appointments', undefined, { qs: { date: this.fixtures.tomorrow } }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.totalItems).to.equal(1)
    })
  })

  it('allowsOnlyOneRescheduleWhenTwoAppointmentsRaceForTheSameSlot', function () {
    create('POST', '/api/appointments', this.booking).then((first) => {
      create('POST', '/api/appointments', { ...this.booking, startAt: `${this.fixtures.tomorrow}T14:00:00+07:00` }).then((second) => {
        const target = { startAt: `${this.fixtures.tomorrow}T11:00:00+07:00` }
        cy.parallelApi([patch(`/api/appointments/${first.id}`, target), patch(`/api/appointments/${second.id}`, target)])
          .then((responses) => expectOneWinner(responses))
        cy.api('GET', `/api/appointments/${first.id}`).then(({ status, body: savedFirst }) => {
          expect(status).to.equal(200)
          cy.api('GET', `/api/appointments/${second.id}`).then(({ status: otherStatus, body: savedSecond }) => {
            expect(otherStatus).to.equal(200)
            expect(savedFirst.startAt).not.to.equal(savedSecond.startAt)
          })
        })
      })
    })
  })

  it('allowsOnlyOneCheckInWhenTwoRequestsRace', function () {
    create('POST', '/api/appointments', this.booking).then(({ id }) => {
      cy.parallelApi([post(`/api/appointments/${id}/check-in`), post(`/api/appointments/${id}/check-in`)])
        .then((responses) => expectOneWinner(responses))
      cy.api('GET', `/api/appointments/${id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.status).to.equal('ARRIVED')
      })
    })
  })

  it('keepsCancellationConsistentWhenCheckInAndCancelRace', function () {
    create('POST', '/api/appointments', this.booking).then(({ id }) => {
      cy.parallelApi([post(`/api/appointments/${id}/check-in`), post(`/api/appointments/${id}/cancel`, { reason: 'ผู้ป่วยติดธุระ' })])
        .then((responses) => {
          expect(successful(responses)).to.have.length.at.least(1)
          expect(responses.every(({ status }) => [200, 409].includes(status))).to.equal(true)
        })
      cy.api('GET', `/api/appointments/${id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.status).to.equal('CANCELLED')
        expect(body.cancelReason).to.be.a('string').and.not.be.empty
      })
    })
  })

  it('keepsStateConsistentWhenRescheduleAndCancelRace', function () {
    create('POST', '/api/appointments', this.booking).then(({ id }) => {
      const startAt = `${this.fixtures.tomorrow}T12:00:00+07:00`
      cy.parallelApi([patch(`/api/appointments/${id}`, { startAt }), post(`/api/appointments/${id}/cancel`, { reason: 'ผู้ป่วยติดธุระ' })])
        .then((responses) => {
          expect(successful(responses)).to.have.length.at.least(1)
          expect(responses.every(({ status }) => [200, 409].includes(status))).to.equal(true)
          cy.api('GET', `/api/appointments/${id}`).then(({ status, body }) => {
            expect(status).to.equal(200)
            if (responses[0].status === 200) expect(Date.parse(body.startAt)).to.equal(Date.parse(startAt))
            if (responses[1].status === 200) expect(body.status).to.equal('CANCELLED')
          })
        })
    })
  })
})

describe('CourseLedgerConcurrencyTest via concurrent HTTP transactions', () => {
  beforeEach(() => {
    cy.registerPatient().then((patient) => {
      create('POST', '/api/courses', { code: 'ONE', name: 'คอร์สครั้งเดียว', price: 1000, sessions: 1, bonusSessions: 0, validityDays: 30 })
        .then((course) => create('POST', '/api/course-purchases', { patientId: patient.id, courseId: course.id, branchCode: 'RP' }).as('purchase'))
    })
  })

  it('allowsOnlyOneUsageWhenTwoRequestsRaceForTheLastSession', function () {
    const request = post('/api/course-usages', { coursePurchaseId: this.purchase.id, note: 'แย่งครั้งสุดท้าย' })
    cy.parallelApi([request, request]).then((responses) => expectOneWinner(responses, 201))
    cy.api('GET', `/api/course-purchases/${this.purchase.id}`).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.purchase.remaining).to.equal(0)
      expect(body.movements.filter(({ type }) => type === 'USAGE')).to.have.length(1)
    })
  })

  it('doesNotDoubleReverseWhenTwoRequestsRaceOnTheSameUsage', function () {
    create('POST', '/api/course-usages', { coursePurchaseId: this.purchase.id })
    cy.api('GET', `/api/course-purchases/${this.purchase.id}`).then(({ status, body }) => {
      expect(status).to.equal(200)
      const usage = body.movements.find(({ type }) => type === 'USAGE')
      expect(usage).to.exist
      const request = post(`/api/course-movements/${usage.id}/reverse`, { reason: 'บันทึกผิด' })
      cy.parallelApi([request, request]).then((responses) => expectOneWinner(responses))
      cy.api('GET', `/api/course-purchases/${this.purchase.id}`).then(({ status: readStatus, body: ledger }) => {
        expect(readStatus).to.equal(200)
        expect(ledger.purchase.remaining).to.equal(1)
      })
    })
  })
})

describe('SaleConcurrencyTest via concurrent HTTP transactions', () => {
  beforeEach(() => {
    cy.registerPatient().then((patient) => {
      create('POST', '/api/courses', { code: 'RACE-SALE', name: 'คอร์สทดสอบพร้อมกัน', price: 5000, sessions: 4, bonusSessions: 0, validityDays: 30 })
        .then((course) => cy.wrap(saleBody(course.id, patient.id, 5000)).as('sale'))
    })
  })

  it('neverIssuesTheSameReceiptNumberTwice', function () {
    cy.parallelApi([post('/api/payments', this.sale), post('/api/payments', this.sale)]).then((responses) => {
      expect(responses.map(({ status }) => status)).to.deep.equal([201, 201])
      expect(new Set(responses.map(({ body }) => body.receiptNo)).size).to.equal(2)
    })
  })

  it('doesNotRefundTwiceWhenTwoRequestsVoidTheSameReceipt', function () {
    create('POST', '/api/payments', this.sale).then((sale) => {
      const request = post(`/api/payments/${sale.id}/reverse`, { reason: 'กดพร้อมกัน' })
      cy.parallelApi([request, request]).then((responses) => expectOneWinner(responses))
      cy.api('GET', `/api/receipts/${sale.id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.paid).to.equal(0)
        expect(body.payments).to.have.length(2)
      })
      cy.api('GET', `/api/course-purchases/${sale.items[0].coursePurchaseId}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.purchase.status).to.equal('CANCELLED')
      })
    })
  })
})
