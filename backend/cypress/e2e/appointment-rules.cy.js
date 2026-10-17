import { appointmentBody } from '../support/commands'

const at = (fixtures, time) => `${fixtures.tomorrow}T${time}:00+07:00`
const instant = (value) => new Date(value).toISOString()
function request(method, url, payload, expected = 200, message) {
  return cy.api(method, url, payload).then(({ status, body }) => {
    expect(status).to.equal(expected)
    if (message) expect(body.message).to.include(message)
    return body
  })
}
const book = (body) => request('POST', '/api/appointments', body, 201)
const action = (id, name, expected = 200, message) => request('POST', `/api/appointments/${id}/${name}`,
  name === 'cancel' ? { reason: 'ผู้ป่วยติดธุระ' } : undefined, expected, message)
const move = (id, startAt, therapistId = null, expected = 200, message) =>
  request('PATCH', `/api/appointments/${id}`, { startAt, therapistId }, expected, message)
const fixture = (change) => request('POST', '/__cypress/appointment-fixtures', change)

beforeEach(function () {
  cy.registerPatient().then((patient) => cy.wrap(appointmentBody(this.fixtures, patient.id)).as('booking'))
  request('GET', '/__cypress/appointment-fixtures').then((body) => cy.wrap(body.otherTherapistId).as('otherTherapistId'))
})

describe('AppointmentStateTest migrated through real lifecycle HTTP APIs', () => {
  for (const [name, steps, status] of [
    ['newAppointmentStartsAsConfirmed', [], 'CONFIRMED'],
    ['checkInMovesConfirmedToArrived', ['check-in'], 'ARRIVED'],
    ['startServiceMovesArrivedToInService', ['check-in', 'start-service'], 'IN_SERVICE'],
    ['completeMovesInServiceToCompleted', ['check-in', 'start-service', 'complete'], 'COMPLETED'],
    ['noShowMovesConfirmedToNoShow', ['no-show'], 'NO_SHOW'],
    ['cancelIsAllowedAfterArrival', ['check-in', 'cancel'], 'CANCELLED'],
  ]) {
    it(name, function () {
      book(this.booking).then((appointment) => {
        let result = cy.wrap(appointment)
        for (const step of steps) result = action(appointment.id, step)
        result.its('status').should('equal', status)
      })
    })
  }
  it('rescheduleMovesConfirmedToRescheduled', function () {
    book(this.booking).then(({ id }) => move(id, at(this.fixtures, '12:00')).then((body) => {
      expect(body.status).to.equal('RESCHEDULED')
      expect(instant(body.startAt)).to.equal(instant(at(this.fixtures, '12:00')))
    }))
  })
  it('rescheduledAppointmentCanBeRescheduledAgain', function () {
    book(this.booking).then(({ id }) => {
      move(id, at(this.fixtures, '12:00'))
      move(id, at(this.fixtures, '14:00')).its('status').should('equal', 'RESCHEDULED')
    })
  })
  it('rescheduledAppointmentCanCheckIn', function () {
    book(this.booking).then(({ id }) => {
      move(id, at(this.fixtures, '12:00'))
      action(id, 'check-in').its('status').should('equal', 'ARRIVED')
    })
  })
  it('cancelStoresReason', function () {
    book(this.booking).then(({ id }) => action(id, 'cancel').should('include', {
      status: 'CANCELLED', cancelReason: 'ผู้ป่วยติดธุระ',
    }))
  })
  for (const [name, prior, next, message] of [
    ['rejectsStartServiceBeforeArrival', [], 'start-service', 'CONFIRMED'],
    ['rejectsCompleteBeforeServiceStarted', ['check-in'], 'complete', 'ARRIVED'],
    ['rejectsSecondCheckIn', ['check-in'], 'check-in'],
    ['rejectsCancelAfterCompleted', ['check-in', 'start-service', 'complete'], 'cancel', 'COMPLETED'],
    ['rejectsCancelAfterServiceStarted', ['check-in', 'start-service'], 'cancel', 'IN_SERVICE'],
    ['rejectsCheckInAfterCancel', ['cancel'], 'check-in'],
    ['rejectsNoShowAfterArrival', ['check-in'], 'no-show', 'ARRIVED'],
  ]) {
    it(name, function () {
      book(this.booking).then(({ id }) => {
        prior.forEach((step) => action(id, step))
        action(id, next, 409, message)
      })
    })
  }
  it('rejectsRescheduleAfterArrival', function () {
    book(this.booking).then(({ id }) => {
      action(id, 'check-in')
      move(id, at(this.fixtures, '12:00'), null, 409)
    })
  })
  it('cancelledAndNoShowReleaseTheTimeSlot', () => {
    request('GET', '/__cypress/appointment-slot-releasing').should('have.members', ['CANCELLED', 'NO_SHOW']).and('have.length', 2)
  })
  it('updatesTimestampOnTransition', function () {
    book(this.booking).then(({ id }) => {
      request('GET', `/__cypress/appointment-timestamp/${id}`).then(({ updatedAt }) => {
        action(id, 'check-in')
        request('GET', `/__cypress/appointment-timestamp/${id}`).then((after) => {
          expect(Date.parse(after.updatedAt)).to.be.greaterThan(Date.parse(updatedAt))
        })
      })
    })
  })
})

describe('AppointmentBookingServiceTest migrated to HTTP', () => {
  it('createsConfirmedAppointmentUsingServiceDuration', function () {
    book(this.booking).then((body) => {
      expect(body.id).not.to.be.null
      expect(body).to.include({ status: 'CONFIRMED', branchCode: '01' })
      expect(Date.parse(body.endAt) - Date.parse(body.startAt)).to.equal(60 * 60 * 1000)
      expect(body.patient.hn.trim()).not.to.be.empty
      expect(body.service.durationMinutes).to.equal(60)
    })
  })
  it('rejectsOverlappingAppointmentForSameTherapist', function () {
    book(this.booking)
    request('POST', '/api/appointments', { ...this.booking, startAt: at(this.fixtures, '09:30') }, 409, 'นัดหมายในช่วงเวลานี้แล้ว')
  })
  it('allowsBackToBackAppointmentForSameTherapist', function () {
    book(this.booking)
    book({ ...this.booking, startAt: at(this.fixtures, '10:00') }).its('id').should('be.a', 'number')
  })
  it('rejectsStartTimeInThePast', function () {
    request('POST', '/api/appointments', { ...this.booking, startAt: '2000-01-01T09:00:00+07:00' }, 400, 'ย้อนหลัง')
  })
  it('rejectsUnknownPatient', function () {
    request('POST', '/api/appointments', { ...this.booking, patientId: 999999 }, 400, 'ไม่พบผู้ป่วย')
  })
  it('rejectsInactiveService', function () {
    fixture({ inactiveService: true })
    request('POST', '/api/appointments', this.booking, 400, 'บริการถูกปิดการใช้งาน')
  })
  it('rejectsTherapistFromAnotherBranch', function () {
    fixture({ otherBranch: true })
    request('POST', '/api/appointments', { ...this.booking, branchCode: '99' }, 400, 'ไม่ได้สังกัดสาขานี้')
  })
})

describe('AppointmentRescheduleServiceTest migrated to HTTP', () => {
  it('reschedulesToNewTimeKeepingSameDuration', function () {
    book(this.booking).then(({ id }) => move(id, at(this.fixtures, '12:00')).then((body) => {
      expect(instant(body.startAt)).to.equal(instant(at(this.fixtures, '12:00')))
      expect(Date.parse(body.endAt) - Date.parse(body.startAt)).to.equal(3600000)
      expect(body.status).to.equal('RESCHEDULED')
    }))
  })
  it('reschedulesToDifferentTherapistInSameBranch', function () {
    book(this.booking).then(({ id }) => move(id, at(this.fixtures, '12:00'), this.otherTherapistId)
      .its('therapist.id').should('equal', this.otherTherapistId))
  })
  it('keepsOriginalTherapistWhenNotProvided', function () {
    book(this.booking).then(({ id }) => move(id, at(this.fixtures, '12:00')).its('therapist.id').should('equal', this.fixtures.therapistId))
  })
  it('allowsReschedulingOntoItsOwnOriginalSlot', function () {
    book(this.booking).then(({ id }) => move(id, this.booking.startAt).then((body) => {
      expect(instant(body.startAt)).to.equal(instant(this.booking.startAt))
    }))
  })
  it('rejectsRescheduleOfArrivedAppointment', function () {
    book(this.booking).then(({ id }) => {
      action(id, 'check-in')
      move(id, at(this.fixtures, '12:00'), null, 409, 'ARRIVED')
    })
  })
  it('rejectsRescheduleOntoOverlappingSlot', function () {
    book(this.booking).then(({ id }) => {
      book({ ...this.booking, startAt: at(this.fixtures, '11:00') })
      move(id, at(this.fixtures, '11:15'), null, 409, 'นัดหมายในช่วงเวลานี้แล้ว')
    })
  })
  it('allowsReschedulingBackToBackWithAnotherAppointment', function () {
    book(this.booking).then(({ id }) => {
      book({ ...this.booking, startAt: at(this.fixtures, '11:00') })
      move(id, at(this.fixtures, '10:00')).then((body) => expect(instant(body.endAt)).to.equal(instant(at(this.fixtures, '11:00'))))
    })
  })
  it('rejectsRescheduleToPastTime', function () {
    book(this.booking).then(({ id }) => move(id, '2000-01-01T09:00:00+07:00', null, 400, 'ย้อนหลัง'))
  })
  it('rejectsInactiveTherapist', function () {
    book(this.booking).then(({ id }) => {
      fixture({ inactiveTherapist: true })
      move(id, at(this.fixtures, '12:00'), this.otherTherapistId, 400, 'นักกายภาพบำบัดถูกปิดการใช้งาน')
    })
  })
  it('rejectsTherapistFromAnotherBranch', function () {
    book(this.booking).then(({ id }) => {
      fixture({ otherBranch: true }).then(({ otherBranchTherapistId }) => {
        move(id, at(this.fixtures, '12:00'), otherBranchTherapistId, 400, 'ไม่ได้สังกัดสาขานี้')
      })
    })
  })
  it('rejectsRescheduleOfMissingAppointment', function () {
    move(999999, at(this.fixtures, '12:00'), null, 404)
  })
})

describe('AppointmentRepositoryTest via real repository access', () => {
  beforeEach(function () { book(this.booking).as('existing') })
  function overlap(context, start, end, expected, therapistId = context.fixtures.therapistId, excludeAppointmentId = null) {
    return request('POST', '/__cypress/appointment-overlap', {
      therapistId, startAt: instant(at(context.fixtures, start)), endAt: instant(at(context.fixtures, end)), excludeAppointmentId,
    }).should('equal', expected)
  }
  for (const [name, start, end, expected] of [
    ['detectsPartialOverlap', '09:30', '10:30', true],
    ['detectsFullyContainedAppointment', '09:15', '09:45', true],
    ['detectsEnclosingAppointment', '08:00', '11:00', true],
    ['allowsBackToBackAppointmentAfter', '10:00', '11:00', false],
    ['allowsBackToBackAppointmentBefore', '08:00', '09:00', false],
  ]) it(name, function () { overlap(this, start, end, expected) })
  it('allowsSameTimeForAnotherTherapist', function () { overlap(this, '09:00', '10:00', false, this.otherTherapistId) })
  for (const [name, time, end, step] of [
    ['ignoresCancelledAppointments', '13:00', '14:00', 'cancel'],
    ['ignoresNoShowAppointments', '15:00', '16:00', 'no-show'],
  ]) it(name, function () {
    book({ ...this.booking, therapistId: this.otherTherapistId, startAt: at(this.fixtures, time) }).then(({ id }) => {
      action(id, step)
      overlap(this, time, end, false, this.otherTherapistId)
    })
  })
  it('stillDetectsRescheduledAppointments', function () {
    book({ ...this.booking, therapistId: this.otherTherapistId, startAt: at(this.fixtures, '17:00') }).then(({ id }) => {
      move(id, at(this.fixtures, '18:00'), this.otherTherapistId)
      overlap(this, '18:00', '19:00', true, this.otherTherapistId)
    })
  })
  it('excludingAppointmentAllowsRescheduleOntoSameSlot', function () {
    overlap(this, '09:00', '10:00', false, this.fixtures.therapistId, this.existing.id)
  })
  it('excludingAppointmentStillDetectsOtherOverlaps', function () {
    book({ ...this.booking, startAt: at(this.fixtures, '10:30') })
    overlap(this, '10:15', '10:45', true, this.fixtures.therapistId, this.existing.id)
  })
})

describe('AppointmentQueryServiceTest migrated to HTTP', () => {
  beforeEach(function () {
    book(this.booking).as('first')
    book({ ...this.booking, startAt: at(this.fixtures, '11:00') })
    book({ ...this.booking, therapistId: this.otherTherapistId })
  })
  function find(context, extra = {}, expected = 200, message) {
    const query = new URLSearchParams({ branchCode: '01', date: context.fixtures.tomorrow, ...extra })
    return request('GET', `/api/appointments?${query}`, undefined, expected, message)
  }
  it('returnsAppointmentsOfTheDaySortedByStartTime', function () {
    find(this).then((body) => {
      expect(body.totalItems).to.equal(3)
      const starts = body.items.map((item) => Date.parse(item.startAt))
      expect(starts).to.deep.equal([...starts].sort((a, b) => a - b))
    })
  })
  it('filtersByTherapist', function () {
    find(this, { therapistId: this.otherTherapistId }).then((body) => {
      expect(body.totalItems).to.equal(1)
      expect(body.items[0].therapist.id).to.equal(this.otherTherapistId)
    })
  })
  it('filtersByStatus', function () {
    action(this.first.id, 'cancel')
    find(this, { status: 'CANCELLED' }).its('totalItems').should('equal', 1)
    find(this, { status: 'CONFIRMED' }).its('totalItems').should('equal', 2)
  })
  it('returnsEmptyPageForDayWithoutAppointments', function () {
    const day = new Date(`${this.fixtures.tomorrow}T00:00:00Z`)
    day.setUTCDate(day.getUTCDate() + 30)
    find(this, { date: day.toISOString().slice(0, 10) }).then((body) => {
      expect(body.items).to.be.empty
      expect(body.totalItems).to.equal(0)
      expect(body.totalPages).to.equal(0)
    })
  })
  it('paginatesInDatabase', function () {
    find(this, { page: 1, size: 2 }).then((body) => {
      expect(body.items).to.have.length(1)
      expect(body).to.include({ page: 1, totalItems: 3, totalPages: 2 })
    })
  })
  it('rejectsInvalidPagination', function () {
    request('GET', '/api/appointments?page=-1&size=20', undefined, 400)
    request('GET', '/api/appointments?page=0&size=101', undefined, 400)
  })
  it('rejectsUnknownBranchCode', function () { find(this, { branchCode: 'ZZ' }, 400, 'ไม่พบสาขา') })
  it('returnsAppointmentById', function () {
    request('GET', `/api/appointments/${this.first.id}`).its('id').should('equal', this.first.id)
  })
  it('throwsWhenAppointmentDoesNotExist', function () {
    request('GET', '/api/appointments/999999', undefined, 404, '999999')
  })
})
