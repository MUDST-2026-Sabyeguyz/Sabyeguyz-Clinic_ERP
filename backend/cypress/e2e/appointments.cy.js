import { appointmentBody } from '../support/commands'

describe('Appointment booking and lifecycle API', () => {
  beforeEach(function () {
    cy.registerPatient().then((patient) => {
      cy.wrap(appointmentBody(this.fixtures, patient.id)).as('booking')
    })
  })

  function book(body) {
    return cy.api('POST', '/api/appointments', body).then(({ status, body: appointment }) => {
      expect(status).to.equal(201)
      return appointment
    })
  }

  function action(id, name, expectedStatus = 200, body) {
    return cy.api('POST', `/api/appointments/${id}/${name}`, body).then((response) => {
      expect(response.status).to.equal(expectedStatus)
      return response.body
    })
  }

  it('creates a confirmed appointment with Location and service details', function () {
    cy.api('POST', '/api/appointments', this.booking).then(({ status, headers, body }) => {
      expect(status).to.equal(201)
      expect(headers.location).to.include(`/api/appointments/${body.id}`)
      expect(body).to.include({ status: 'CONFIRMED', branchCode: '01' })
      expect(body.patient.hn).not.to.be.empty
      expect(body.service.durationMinutes).to.equal(60)
    })
  })

  it('rejects overlapping appointments with 409', function () {
    book(this.booking)
    cy.api('POST', '/api/appointments', { ...this.booking, startAt: `${this.fixtures.tomorrow}T09:30:00+07:00` })
      .then(({ status, body }) => {
        expect(status).to.equal(409)
        expect(body.message).to.be.a('string').and.not.be.empty
      })
  })

  it('allows back-to-back appointments', function () {
    book(this.booking)
    book({ ...this.booking, startAt: `${this.fixtures.tomorrow}T10:00:00+07:00` })
  })

  it('rejects a missing patient ID', function () {
    const { patientId, ...invalid } = this.booking
    cy.api('POST', '/api/appointments', invalid).then(({ status, body }) => {
      expect(status).to.equal(400)
      expect(body.fields).to.have.property('patientId')
    })
  })

  for (const [name, override] of [['unknown therapist', { therapistId: 999999 }], ['malformed startAt', { startAt: '1 กันยายน 2026' }]]) {
    it(`rejects ${name}`, function () {
      cy.api('POST', '/api/appointments', { ...this.booking, ...override }).then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.message).to.be.a('string').and.not.be.empty
      })
    })
  }

  it('reschedules an appointment with PATCH', function () {
    book(this.booking).then(({ id }) => {
      const startAt = `${this.fixtures.tomorrow}T14:00:00+07:00`
      cy.api('PATCH', `/api/appointments/${id}`, { startAt }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(Date.parse(body.startAt)).to.equal(Date.parse(startAt))
      })
    })
  })

  it('rejects rescheduling into an occupied slot', function () {
    const startAt = `${this.fixtures.tomorrow}T14:00:00+07:00`
    book(this.booking).then(({ id }) => {
      book({ ...this.booking, startAt })
      cy.api('PATCH', `/api/appointments/${id}`, { startAt }).its('status').should('equal', 409)
    })
  })

  it('rejects rescheduling after check-in', function () {
    book(this.booking).then(({ id }) => {
      action(id, 'check-in')
      cy.api('PATCH', `/api/appointments/${id}`, { startAt: `${this.fixtures.tomorrow}T14:00:00+07:00` })
        .its('status').should('equal', 409)
    })
  })

  it('returns 404 when rescheduling a missing appointment', function () {
    cy.api('PATCH', '/api/appointments/999999', { startAt: `${this.fixtures.tomorrow}T14:00:00+07:00` })
      .its('status').should('equal', 404)
  })

  for (const scenario of [
    { name: 'checks in a confirmed appointment', steps: ['check-in'], status: 'ARRIVED' },
    { name: 'starts service after check-in', steps: ['check-in', 'start-service'], status: 'IN_SERVICE' },
    { name: 'completes service after it starts', steps: ['check-in', 'start-service', 'complete'], status: 'COMPLETED' },
    { name: 'marks an appointment as no-show', steps: ['no-show'], status: 'NO_SHOW' },
  ]) {
    it(scenario.name, function () {
      book(this.booking).then(({ id }) => {
        for (const step of scenario.steps.slice(0, -1)) action(id, step)
        action(id, scenario.steps.at(-1)).its('status').should('equal', scenario.status)
      })
    })
  }

  for (const scenario of [
    { name: 'start-service before check-in', prior: [], next: 'start-service', message: true },
    { name: 'complete before service starts', prior: ['check-in'], next: 'complete', message: true },
    { name: 'no-show after arrival', prior: ['check-in'], next: 'no-show' },
    { name: 'a second check-in', prior: ['check-in'], next: 'check-in' },
    { name: 'cancellation after completion', prior: ['check-in', 'start-service', 'complete'], next: 'cancel', body: { reason: 'เปลี่ยนใจ' } },
  ]) {
    it(`rejects ${scenario.name} with 409`, function () {
      book(this.booking).then(({ id }) => {
        for (const step of scenario.prior) action(id, step)
        action(id, scenario.next, 409, scenario.body).then((body) => {
          if (scenario.message) expect(body.message).to.be.a('string').and.not.be.empty
        })
      })
    })
  }

  it('cancels an appointment with the supplied reason', function () {
    book(this.booking).then(({ id }) => {
      action(id, 'cancel', 200, { reason: 'ผู้ป่วยติดธุระ' }).should('include', {
        status: 'CANCELLED', cancelReason: 'ผู้ป่วยติดธุระ',
      })
    })
  })

  it('rejects cancellation without a reason', function () {
    book(this.booking).then(({ id }) => {
      action(id, 'cancel', 400, { reason: '   ' }).its('fields').should('have.property', 'reason')
    })
  })

  for (const step of ['no-show', 'cancel']) {
    it(`releases the booked slot after ${step}`, function () {
      book(this.booking).then(({ id }) => {
        action(id, step, 200, step === 'cancel' ? { reason: 'ผู้ป่วยยกเลิก' } : undefined)
        book(this.booking)
      })
    })
  }

  it('returns 404 when checking in a missing appointment', () => {
    action(999999, 'check-in', 404).its('message').should('equal', 'ไม่พบการนัดหมาย id 999999')
  })

  it('returns 404 when cancelling a missing appointment', () => {
    action(999999, 'cancel', 404, { reason: 'ทดสอบ' })
  })
})
