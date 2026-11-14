/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai assertions. */
import { api, fixtures, patient, slot, visit } from '../support-therapist/helpers'

const asManager = (method, path, body) => api(method, path, body, 'manager')
const book = (patientId, time = '09:00') => asManager('POST', '/api/appointments', {
  branchCode: '01', patientId, therapistId: fixtures.therapistId, serviceId: fixtures.singleVisitServiceId,
  ...slot(fixtures.tomorrow, time), status: 'Unconfirmed',
})

describe('Manager appointments', () => {
  it('books an unconfirmed appointment for a registered patient', () => {
    patient().then((p) => book(p.id).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.status).to.equal('UNCONFIRMED')
      expect(body.patient.id).to.equal(p.id)
    }))
  })

  it('refuses a second booking for the same therapist and time (400) and keeps the first', () => {
    patient().then((p) => book(p.id).its('status').should('equal', 201).then(() => {
      book(p.id).then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.message).to.contain('ไม่ว่าง')
      })
      asManager('GET', '/api/appointments?size=10').its('body.totalItems').should('equal', 1)
    }))
  })

  it('reschedules an appointment to another time and persists it', () => {
    patient().then((p) => book(p.id).then(({ body: a }) => {
      asManager('PATCH', `/api/appointments/${a.id}`, slot(fixtures.tomorrow, '13:00')).its('status').should('equal', 200)
      asManager('GET', `/api/appointments/${a.id}`).then(({ body }) => {
        expect(new Date(body.startAt).getTime()).to.equal(new Date(`${fixtures.tomorrow}T13:00:00+07:00`).getTime())
      })
    }))
  })

  it('requires a reason to cancel, then cancels and keeps the reason', () => {
    patient().then((p) => book(p.id).then(({ body: a }) => {
      asManager('POST', `/api/appointments/${a.id}/cancel`, { reason: '' }).then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.message).to.contain('เหตุผล')
      })
      asManager('GET', `/api/appointments/${a.id}`).its('body.status').should('equal', 'UNCONFIRMED')
      asManager('POST', `/api/appointments/${a.id}/cancel`, { reason: 'ผู้ป่วยขอยกเลิก' }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.status).to.equal('CANCELLED')
      })
      asManager('GET', `/api/appointments/${a.id}`).then(({ body }) => {
        expect(body.status).to.equal('CANCELLED')
        expect(body.cancelReason).to.equal('ผู้ป่วยขอยกเลิก')
      })
    }))
  })

  it('shows the booked appointment in the appointment list for the Manager', () => {
    patient().then((p) => book(p.id).its('status').should('equal', 201).then(() => {
      visit('/staff/appointments', 'manager')
      cy.contains('tr', p.fullName).should('be.visible')
    }))
  })
})
