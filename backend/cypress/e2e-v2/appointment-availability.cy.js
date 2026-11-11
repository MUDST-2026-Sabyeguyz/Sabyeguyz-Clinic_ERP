/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const at = (fx, time) => `${fx.tomorrow}T${time}:00+07:00`
const oneHourAfter = (fx, time) =>
    new Date(Date.parse(at(fx, time)) + 60 * 60 * 1000).toISOString()

const branch01 = () => cy.api('GET', '/api/branches').then(({ body }) => body.find((row) => row.branchCode === '01'))
const putBranch = (branch, extra) => cy.api('PUT', `/api/branches/${branch.id}`, {
  branchCode: branch.branchCode, name: branch.name, address: branch.address, phone: branch.phone, ...extra,
})
const slots = (fx, therapistId, extra = '') => cy.api('GET',
  `/api/appointments/availability?branchCode=01&therapistId=${therapistId}&date=${fx.tomorrow}${extra}`).its('body')

describe('v2 branch booking settings', () => {
  it('stores the email, licence, booking window and default appointment length of a branch', () => {
    branch01().then((branch) => {
      putBranch(branch, {
        email: 'rama9@clinic.test', licenseNo: 'PT-1234', bookingOpenTime: '09:00', bookingCloseTime: '18:00',
        defaultAppointmentMinutes: 60,
      }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.include({
          email: 'rama9@clinic.test', licenseNo: 'PT-1234', bookingOpenTime: '09:00', bookingCloseTime: '18:00',
          defaultAppointmentMinutes: 60,
        })
      })
      cy.api('GET', `/api/branches/${branch.id}`).its('body.bookingCloseTime').should('equal', '18:00')
    })
  })

  it('leaves every optional setting empty for a client that sends only the four required fields', () => {
    branch01().then((branch) => putBranch(branch, {}).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.email).to.equal(null)
      expect(body.bookingOpenTime).to.equal(null)
      expect(body.defaultAppointmentMinutes).to.equal(null)
    }))
  })

  it('refuses a half-set window, a reversed window, a bad email and a nonpositive length', () => {
    branch01().then((branch) => {
      putBranch(branch, { bookingOpenTime: '09:00' }).its('status').should('equal', 400)
      putBranch(branch, { bookingOpenTime: '18:00', bookingCloseTime: '09:00' }).its('status').should('equal', 400)
      putBranch(branch, { bookingOpenTime: '9am', bookingCloseTime: '18:00' }).its('status').should('equal', 400)
      putBranch(branch, { email: 'not-an-email' }).its('status').should('equal', 400)
      putBranch(branch, { defaultAppointmentMinutes: 0 }).its('status').should('equal', 400)
      putBranch(branch, { defaultAppointmentMinutes: -1 }).its('status').should('equal', 400)
      putBranch(branch, { defaultAppointmentMinutes: 1 }).its('body.defaultAppointmentMinutes').should('equal', 1)
      putBranch(branch, { defaultAppointmentMinutes: 600 }).its('body.defaultAppointmentMinutes').should('equal', 600)
    })
  })
})

describe('v2 service appointment length and course validity units', () => {
  it('keeps the optional appointment length of a single-visit service', function () {
    cy.api('GET', '/api/services').then(({ body }) => {
      const service = body.find((row) => row.id === this.fixtures.singleVisitServiceId)
      const send = (minutes) => cy.api('PUT', `/api/services/${service.id}`, {
        code: service.code, name: service.name, price: service.price, defaultDurationMinutes: minutes,
      })
      send(45).its('body.defaultDurationMinutes').should('equal', 45)
      send(null).its('body.defaultDurationMinutes').should('equal', 0)
      send(1).its('body.defaultDurationMinutes').should('equal', 1)
      send(600).its('body.defaultDurationMinutes').should('equal', 600)
      send(0).its('status').should('equal', 400)
      send(-1).its('status').should('equal', 400)
    })
  })

  it('sells a course whose validity is counted in months', function () {
    cy.api('GET', `/api/courses/${this.fixtures.courseId}`).then(({ body: course }) => {
      cy.api('PUT', `/api/courses/${course.id}`, {
        code: course.code, name: course.name, price: course.price, sessions: course.sessions,
        bonusSessions: course.bonusSessions, validityValue: 6, validityUnit: 'Month', defaultDurationMinutes: 45,
      }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.include({ validityValue: 6, validityUnit: 'Month', defaultDurationMinutes: 45 })
      })
    })
    cy.registerPatient().then((patient) => cy.api('POST', '/api/course-purchases', {
      patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', caseOwnerStaffId: this.fixtures.therapistId, paymentLabelCode: 'FULL',
    }).then(({ status, body }) => {
      expect(status).to.be.oneOf([200, 201])
      const days = (new Date(body.expiresAt) - new Date()) / 86400000
      expect(days).to.be.within(170, 190)
    }))
  })

  it('still accepts the older validityDays-only request', function () {
    cy.api('GET', `/api/courses/${this.fixtures.courseId}`).then(({ body: course }) => {
      cy.api('PUT', `/api/courses/${course.id}`, {
        code: course.code, name: course.name, price: course.price, sessions: course.sessions,
        bonusSessions: course.bonusSessions, validityDays: 90,
      }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.include({ validityValue: 90, validityUnit: 'Day', validityDays: 90 })
      })
    })
  })
})

describe('v2 free times and overlapping appointments', () => {
  const window9to12 = () => branch01().then((branch) => putBranch(branch, {
    bookingOpenTime: '09:00', bookingCloseTime: '12:00', defaultAppointmentMinutes: 60,
  }).its('status').should('equal', 200))

  it('lists the start times inside the booking window that still fit the appointment length', function () {
    window9to12()
    slots(this.fixtures, this.fixtures.therapistId).then((result) => {
      expect(result.windowConfigured).to.equal(true)
      expect(result.durationMinutes).to.equal(60)
      expect(result.slots.map((slot) => slot.time)).to.deep.equal(['09:00', '09:30', '10:00', '10:30', '11:00'])
      expect(result.slots.every((slot) => slot.available)).to.equal(true)
    })
  })

  it('marks the times that overlap an appointment of the therapist, not back-to-back ones nor other therapists', function () {
    window9to12()
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/appointments', {
        branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId, startAt: at(this.fixtures, '10:00'), endAt: oneHourAfter(this.fixtures, '10:00'),
      }).its('status').should('equal', 201)
      slots(this.fixtures, this.fixtures.therapistId).then((result) => {
        const free = Object.fromEntries(result.slots.map((slot) => [slot.time, slot.available]))
        expect(free).to.deep.equal({ '09:00': true, '09:30': false, '10:00': false, '10:30': false, '11:00': true })
      })
      // the other therapist works in branch 02: nothing of branch 01's appointments touches them
      cy.api('GET', `/api/appointments/availability?branchCode=02&therapistId=${this.fixtures.therapist2Id}&date=${this.fixtures.tomorrow}`)
        .its('body.slots').should((all) => expect(all.every((slot) => slot.available)).to.equal(true))
    })
  })

  it('refuses a booking that overlaps or falls outside the window, and accepts a back-to-back one', function () {
    window9to12()
    cy.registerPatient().then((patient) => {
      const book = (time) => cy.api('POST', '/api/appointments', {
        branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId, startAt: at(this.fixtures, time), endAt: oneHourAfter(this.fixtures, time),
      })
      book('10:00').its('status').should('equal', 201)
      book('10:30').its('status').should('equal', 400)
      book('09:30').its('status').should('equal', 400)
      book('11:00').its('status').should('equal', 201)
      book('08:00').its('status').should('equal', 400)
      book('11:30').its('status').should('equal', 400)
    })
  })

  it('frees the times again when the appointment is cancelled', function () {
    window9to12()
    cy.registerPatient().then((patient) => cy.api('POST', '/api/appointments', {
      branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId, startAt: at(this.fixtures, '10:00'), endAt: oneHourAfter(this.fixtures, '10:00'),
    }).then(({ body }) => {
      cy.api('POST', `/api/appointments/${body.id}/cancel`, { reason: 'ผู้ป่วยขอยกเลิก' }).its('status').should('equal', 200)
      slots(this.fixtures, this.fixtures.therapistId).its('slots').should((all) => {
        expect(all.every((slot) => slot.available)).to.equal(true)
      })
    }))
  })

  it('uses the length of the chosen service instead of the branch default', function () {
    window9to12()
    cy.api('GET', '/api/services').then(({ body }) => {
      const service = body.find((row) => row.id === this.fixtures.singleVisitServiceId)
      cy.api('PUT', `/api/services/${service.id}`, {
        code: service.code, name: service.name, price: service.price, defaultDurationMinutes: 30,
      }).its('status').should('equal', 200)
    })
    slots(this.fixtures, this.fixtures.therapistId, `&serviceId=${this.fixtures.singleVisitServiceId}`).then((result) => {
      expect(result.durationMinutes).to.equal(30)
      expect(result.slots.map((slot) => slot.time)).to.include('11:30')
    })
  })

  it('offers no slots when the branch has no booking window', function () {
    slots(this.fixtures, this.fixtures.therapistId).then((result) => {
      expect(result.windowConfigured).to.equal(false)
      expect(result.openTime).to.equal(null)
      expect(result.closeTime).to.equal(null)
      expect(result.slots).to.deep.equal([])
    })
  })

  it('refuses a therapist from another branch', function () {
    cy.api('GET', `/api/appointments/availability?branchCode=02&therapistId=${this.fixtures.therapistId}&date=${this.fixtures.tomorrow}`)
      .its('status').should('equal', 400)
  })
})

describe('v2 appointment without a service', () => {
  it('books a registered patient before any service is chosen', function () {
    branch01().then((branch) => putBranch(branch, {
      bookingOpenTime: '09:00',
      bookingCloseTime: '12:00',
    }).its('status').should('equal', 200))
    cy.registerPatient().then((patient) => cy.api('POST', '/api/appointments', {
      branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId, startAt: at(this.fixtures, '09:00'), endAt: oneHourAfter(this.fixtures, '09:00'),
    }).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.patientType).to.equal('CURRENT')
      expect(body.service.id).to.equal(null)
    }))
  })
})

describe('v2 free times and Busy time in the branch Google Calendar', () => {
  const CAL = 'busy-branch01@group.calendar.google.com'
  const window9to12 = () => branch01().then((branch) => putBranch(branch, {
    bookingOpenTime: '09:00', bookingCloseTime: '12:00', defaultAppointmentMinutes: 60,
  }).its('status').should('equal', 200))
  const connect = () => cy.api('PUT', '/api/calendar/branches/01', { googleCalendarId: CAL }).its('status').should('equal', 200)
  const busy = (fx, from, to) => cy.api('POST', '/__cypress/calendar/busy', {
    calendarId: CAL, start: `${fx.tomorrow}T${from}`, end: `${fx.tomorrow}T${to}`,
  }).its('status').should('equal', 200)
  const book = (fx, patient, time) => cy.api('POST', '/api/appointments', {
    branchCode: '01', patientId: patient.id, therapistId: fx.therapistId, startAt: at(fx, time), endAt: oneHourAfter(fx, time),
  })

  it('says so when the branch has no calendar', function () {
    window9to12()
    slots(this.fixtures, this.fixtures.therapistId).its('googleCalendar').should('equal', 'NOT_CONNECTED')
  })

  it('takes a Busy event typed by hand in Google away from every therapist of the branch', function () {
    window9to12()
    connect()
    busy(this.fixtures, '10:00', '11:00')
    for (const therapistId of [this.fixtures.therapistId]) {
      slots(this.fixtures, therapistId).then((result) => {
        expect(result.googleCalendar).to.equal('CHECKED')
        const bySlot = Object.fromEntries(result.slots.map((slot) => [slot.time, slot]))
        expect(Object.fromEntries(Object.entries(bySlot).map(([time, slot]) => [time, slot.available])))
          .to.deep.equal({ '09:00': true, '09:30': false, '10:00': false, '10:30': false, '11:00': true })
        expect(bySlot['10:00'].reason).to.contain('Google Calendar')
      })
    }
  })

  it('refuses a booking on Busy time and accepts one right beside it', function () {
    window9to12()
    connect()
    busy(this.fixtures, '10:00', '11:00')
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient, '10:00').then(({ status }) => expect(status).to.equal(400))
      book(this.fixtures, patient, '09:30').its('status').should('equal', 400)
      book(this.fixtures, patient, '09:00').its('status').should('equal', 201)
      book(this.fixtures, patient, '11:00').its('status').should('equal', 201)
    })
  })

  it('offers no available slots and refuses booking when Google cannot be read', function () {
    window9to12()
    connect()
    busy(this.fixtures, '10:00', '11:00')

    cy.api('POST', '/__cypress/calendar/fail-busy?count=1')
        .its('status').should('equal', 200)

    slots(this.fixtures, this.fixtures.therapistId).then((result) => {
      expect(result.googleCalendar).to.equal('UNAVAILABLE')
      expect(result.slots.length).to.be.greaterThan(0)
      expect(result.slots.every((slot) => !slot.available)).to.equal(true)
    })

    cy.api('POST', '/__cypress/calendar/fail-busy?count=1')
        .its('status').should('equal', 200)

    cy.registerPatient().then((patient) =>
        book(this.fixtures, patient, '10:00')
            .its('status').should('equal', 409)
    )
  })
})
