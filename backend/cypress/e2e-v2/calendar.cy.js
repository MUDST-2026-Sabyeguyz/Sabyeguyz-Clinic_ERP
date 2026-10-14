// Two-way Google Calendar sync, run against an in-memory fake of Google (see FakeGoogleCalendarClient).
const CAL = 'branch01@group.calendar.google.com'
const at = (fx, time) => `${fx.tomorrow}T${time}:00+07:00`
const local = (fx, time) => `${fx.tomorrow}T${time}` // the fake prints LocalDateTime without zero seconds

const connect = () => cy.api('PUT', '/api/calendar/branches/01', { googleCalendarId: CAL }).its('status').should('equal', 200)
const sync = () => cy.api('POST', '/api/calendar/sync').then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(200)
  return body
})
const events = () => cy.api('GET', `/__cypress/calendar/events?calendarId=${encodeURIComponent(CAL)}`).its('body')
const edit = (body) => cy.api('POST', '/__cypress/calendar/edit', { calendarId: CAL, ...body }).its('status').should('equal', 200)

function book(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/appointments', {
    branchCode: '01', patientId: patient.id, therapistId: fx.therapistId, serviceId: fx.serviceId,
    startAt: at(fx, '09:00'), ...overrides,
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
}

const appointment = (id) => cy.api('GET', `/api/appointments/${id}`).its('body')

describe('v2 Google Calendar sync - system to Google', () => {
  it('writes a booked appointment to the branch calendar with little personal data', function () {
    connect()
    cy.registerPatient({ firstName: 'สมชาย', lastName: 'ใจดี', phone: '0811111111' }).then((patient) => {
      book(this.fixtures, patient).then((appointmentBody) => {
        sync().then((result) => expect(result.pushed).to.equal(1))
        events().then((list) => {
          expect(list).to.have.length(1)
          const event = list[0]
          expect(event.appointmentId).to.equal(appointmentBody.id)
          expect(event.start).to.equal(local(this.fixtures, '09:00'))
          expect(event.end).to.equal(local(this.fixtures, '10:00'))
          expect(event.summary).to.contain(patient.hn).and.to.contain('สมชาย')
          const everything = JSON.stringify(event)
          expect(everything).not.to.contain('ใจดี')
          expect(everything).not.to.contain('0811111111')
        })
      })
    })
  })

  it('moves the event when the appointment is rescheduled and removes it when cancelled', function () {
    connect()
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then((appointmentBody) => {
        sync()
        cy.api('PATCH', `/api/appointments/${appointmentBody.id}`, { startAt: at(this.fixtures, '14:00') }).its('status').should('equal', 200)
        sync().then((result) => expect(result.pushed).to.equal(1))
        events().its('0.start').should('equal', local(this.fixtures, '14:00'))
        cy.api('POST', `/api/appointments/${appointmentBody.id}/cancel`, { reason: 'ทดสอบ' }).its('status').should('equal', 200)
        sync().then((result) => expect(result.removed).to.equal(1))
        events().its('0.cancelled').should('equal', true)
      })
    })
  })

  it('does nothing for a branch without a calendar, and nothing twice (no echo)', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    sync().then((result) => expect(result.pushed).to.equal(0))
    connect()
    sync().then((result) => expect(result.pushed).to.equal(1))
    sync().then((result) => {
      expect(result.pushed).to.equal(0)
      expect(result.movedFromGoogle).to.equal(0)
      expect(result.cancelledFromGoogle).to.equal(0)
      expect(result.revertedInGoogle).to.equal(0)
    })
  })

  it('keeps the booking when Google is down and writes it once Google is back', function () {
    connect()
    cy.api('POST', '/__cypress/calendar/fail?count=1').its('status').should('equal', 200)
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    sync().then((result) => expect(result.failed).to.equal(1))
    cy.api('GET', '/api/calendar/status').its('body.branches').then((branches) => {
      const branch = branches.find((item) => item.branchCode === '01')
      expect(branch.failed).to.equal(1)
      expect(branch.waiting).to.equal(1)
    })
    sync().then((result) => expect(result.pushed).to.equal(1))
    events().should('have.length', 1)
  })

  it('shows an unconfirmed appointment as waiting for confirmation', function () {
    connect()
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    sync()
    events().its('0.summary').should('contain', '[รอยืนยัน]')
  })
})

describe('v2 Google Calendar sync - Google to system', () => {
  it('moves the appointment when the event is moved in Google', function () {
    connect()
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then((appointmentBody) => {
        sync()
        edit({ appointmentId: appointmentBody.id, start: local(this.fixtures, '11:00') })
        sync().then((result) => expect(result.movedFromGoogle).to.equal(1))
        appointment(appointmentBody.id).its('startAt').should('contain', 'T04:00:00')
        sync().then((result) => expect(result.pushed + result.movedFromGoogle + result.revertedInGoogle).to.equal(0))
      })
    })
  })

  it('cancels the appointment when the event is deleted in Google', function () {
    connect()
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then((appointmentBody) => {
        sync()
        edit({ appointmentId: appointmentBody.id, cancel: true })
        sync().then((result) => expect(result.cancelledFromGoogle).to.equal(1))
        appointment(appointmentBody.id).its('status').should('equal', 'CANCELLED')
      })
    })
  })

  it('puts the time back in Google when the new time is already taken', function () {
    connect()
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient)
      cy.registerPatient({ firstName: 'อรุณ', identityNumber: '1100000000999', phone: '0822222222' }).then((second) => {
        book(this.fixtures, second, { startAt: at(this.fixtures, '10:00') }).then((appointmentBody) => {
          sync()
          edit({ appointmentId: appointmentBody.id, start: local(this.fixtures, '09:00') })
          sync().then((result) => {
            expect(result.movedFromGoogle).to.equal(0)
            expect(result.revertedInGoogle).to.equal(1)
          })
          appointment(appointmentBody.id).its('startAt').should('contain', 'T03:00:00')
          events().then((list) => {
            const event = list.find((item) => item.appointmentId === appointmentBody.id)
            expect(event.start).to.equal(local(this.fixtures, '10:00'))
          })
        })
      })
    })
  })

  it('does not accept a change in Google once the patient has arrived', function () {
    connect()
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then((appointmentBody) => {
        cy.api('POST', `/api/appointments/${appointmentBody.id}/check-in`).its('status').should('equal', 200)
        sync()
        edit({ appointmentId: appointmentBody.id, start: local(this.fixtures, '16:00') })
        sync().then((result) => expect(result.revertedInGoogle).to.equal(1))
        appointment(appointmentBody.id).its('status').should('equal', 'ARRIVED')
        events().its('0.start').should('equal', local(this.fixtures, '09:00'))
      })
    })
  })

  it('brings the event back when it is deleted in Google after the visit has started', function () {
    connect()
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then((appointmentBody) => {
        cy.api('POST', `/api/appointments/${appointmentBody.id}/check-in`).its('status').should('equal', 200)
        sync()
        edit({ appointmentId: appointmentBody.id, cancel: true })
        sync().then((result) => expect(result.revertedInGoogle).to.equal(1))
        events().then((list) => expect(list.filter((item) => !item.cancelled)).to.have.length(1))
        appointment(appointmentBody.id).its('status').should('equal', 'ARRIVED')
      })
    })
  })
})

describe('v2 Google Calendar sync - access', () => {
  it('is for the manager only', function () {
    cy.login('therapist', 'therapist123').then(({ body: auth }) => {
      cy.apiAs(auth.token, 'GET', '/api/calendar/status').its('status').should('equal', 403)
      cy.apiAs(auth.token, 'PUT', '/api/calendar/branches/01', { googleCalendarId: CAL }).its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', '/api/calendar/sync').its('status').should('equal', 403)
    })
  })

  it('reports the branches and their calendars, and clears a calendar with an empty value', () => {
    connect()
    cy.api('GET', '/api/calendar/status').then(({ body }) => {
      expect(body.enabled).to.equal(true)
      expect(body.defaultDurationMinutes).to.equal(60)
      expect(body.branches.find((item) => item.branchCode === '01').googleCalendarId).to.equal(CAL)
      expect(body.branches.find((item) => item.branchCode === '02').googleCalendarId).to.equal(null)
    })
    cy.api('PUT', '/api/calendar/branches/01', { googleCalendarId: '' }).then(({ body }) => expect(body.googleCalendarId).to.equal(null))
    cy.api('PUT', '/api/calendar/branches/99', { googleCalendarId: CAL }).its('status').should('equal', 404)
  })
})
