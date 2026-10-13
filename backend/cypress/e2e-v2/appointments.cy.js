const at = (fx, time) => `${fx.tomorrow}T${time}:00+07:00`

function book(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/appointments', {
    branchCode: '01', patientId: patient.id, therapistId: fx.therapistId, serviceId: fx.serviceId,
    startAt: at(fx, '09:00'), ...overrides,
  })
}

describe('v2 appointments', () => {
  it('books a registered patient as Confirmed with patient, therapist and service in the response', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.status).to.equal('CONFIRMED')
        expect(body.patientType).to.equal('CURRENT')
        expect(body.patient.id).to.equal(patient.id)
        expect(body.patient.hn).to.equal(patient.hn)
        expect(body.therapist.id).to.equal(this.fixtures.therapistId)
        expect(body.service.id).to.equal(this.fixtures.serviceId)
      })
    })
  })

  it('refuses a second booking of the same therapist at the same time', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).its('status').should('equal', 201)
      book(this.fixtures, patient).its('status').should('equal', 400)
    })
  })

  it('frees the slot when the first appointment is cancelled', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ body }) => {
        cy.api('POST', `/api/appointments/${body.id}/cancel`, { reason: 'ผู้ป่วยขอยกเลิก' }).then((cancelled) => {
          expect(cancelled.status).to.equal(200)
          expect(cancelled.body.status).to.equal('CANCELLED')
        })
      })
      book(this.fixtures, patient).its('status').should('equal', 201)
    })
  })

  it('allows the same time for a different therapist', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).its('status').should('equal', 201)
      book(this.fixtures, patient, { therapistId: this.fixtures.therapist2Id }).its('status').should('equal', 201)
    })
  })

  it('books an unregistered NEW patient as Unconfirmed, then confirms it', function () {
    cy.api('POST', '/api/appointments', {
      branchCode: '01', therapistId: this.fixtures.therapistId, startAt: at(this.fixtures, '10:00'),
      status: 'Unconfirmed', patientType: 'NEW', contactName: 'สมศรี ใจงาม', contactPhone: '0899999999',
    }).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.status).to.equal('UNCONFIRMED')
      expect(body.patientType).to.equal('NEW')
      expect(body.contactName).to.equal('สมศรี ใจงาม')
      cy.api('POST', `/api/appointments/${body.id}/confirm`).then((confirmed) => {
        expect(confirmed.status).to.equal(200)
        expect(confirmed.body.status).to.equal('CONFIRMED')
      })
    })
  })

  it('links a NEW appointment to a patient registered afterwards', function () {
    cy.api('POST', '/api/appointments', {
      branchCode: '01', therapistId: this.fixtures.therapistId, startAt: at(this.fixtures, '10:30'),
      patientType: 'NEW', contactName: 'สมศรี ใจงาม', contactPhone: '0899999999',
    }).then(({ body }) => {
      cy.registerPatient({ firstName: 'สมศรี', lastName: 'ใจงาม', phone: '0899999999' }).then((patient) => {
        cy.api('POST', `/api/appointments/${body.id}/link-patient`, { patientId: patient.id }).then((linked) => {
          expect(linked.status).to.equal(200)
          expect(linked.body.patient.id).to.equal(patient.id)
        })
      })
    })
  })

  it('walks a visit through check-in, start and complete', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ body }) => {
        const post = (action) => cy.api('POST', `/api/appointments/${body.id}/${action}`)
        post('check-in').then((r) => { expect(r.status).to.equal(200); expect(r.body.status).to.equal('ARRIVED') })
        post('start-service').then((r) => { expect(r.status).to.equal(200); expect(r.body.status).to.equal('IN_SERVICE') })
        post('complete').then((r) => { expect(r.status).to.equal(200); expect(r.body.status).to.equal('COMPLETED') })
        // A completed appointment is final.
        cy.api('POST', `/api/appointments/${body.id}/cancel`, { reason: 'x' }).its('status').should('equal', 400)
      })
    })
  })

  it('rejects start-service before check-in', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ body }) => {
        cy.api('POST', `/api/appointments/${body.id}/start-service`).its('status').should('equal', 400)
      })
    })
  })

  it('marks a no-show', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ body }) => {
        cy.api('POST', `/api/appointments/${body.id}/no-show`).then((r) => {
          expect(r.status).to.equal(200)
          expect(r.body.status).to.equal('NO_SHOW')
        })
      })
    })
  })

  it('reschedules to a new time', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ body }) => {
        cy.api('PATCH', `/api/appointments/${body.id}`, { startAt: at(this.fixtures, '14:00') }).then((r) => {
          expect(r.status).to.equal(200)
          expect(new Date(r.body.startAt).toISOString()).to.equal(new Date(at(this.fixtures, '14:00')).toISOString())
        })
      })
    })
  })

  it('lists and filters appointments by date, therapist and status', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).its('status').should('equal', 201)
      book(this.fixtures, patient, { therapistId: this.fixtures.therapist2Id }).its('status').should('equal', 201)
      cy.api('GET', `/api/appointments?date=${this.fixtures.tomorrow}`).its('body.items').should('have.length', 2)
      cy.api('GET', `/api/appointments?date=${this.fixtures.tomorrow}&therapistId=${this.fixtures.therapistId}`)
        .its('body.items').should('have.length', 1)
      cy.api('GET', `/api/appointments?date=${this.fixtures.tomorrow}&status=Cancelled`)
        .its('body.items').should('have.length', 0)
    })
  })

  it('answers 404 for an unknown appointment and 400 for an unknown patient', function () {
    cy.api('GET', '/api/appointments/999999').its('status').should('equal', 404)
    cy.api('POST', '/api/appointments', {
      branchCode: '01', patientId: 999999, therapistId: this.fixtures.therapistId,
      serviceId: this.fixtures.serviceId, startAt: at(this.fixtures, '09:00'),
    }).its('status').should('equal', 400)
  })
})
