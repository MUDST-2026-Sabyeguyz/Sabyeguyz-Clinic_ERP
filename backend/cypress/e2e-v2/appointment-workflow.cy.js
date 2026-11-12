const at = (fx, time = '09:00') => `${fx.tomorrow}T${time}:00+07:00`
const newBooking = (fx, overrides = {}) => ({ branchCode: '01', therapistId: fx.therapistId,
  startAt: at(fx), patientType: 'NEW', contactName: 'ผู้ป่วย ใหม่', contactPhone: '0891234567', ...overrides })

describe('P3 non-commission appointment workflow', () => {
  it('requires NEW contact name and phone, therapist and time, but no room', function () {
    for (const overrides of [{ contactName: '' }, { contactPhone: '' }, { therapistId: null }, { startAt: null }]) {
      cy.api('POST', '/api/appointments', newBooking(this.fixtures, overrides)).its('status').should('equal', 400)
    }
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
    cy.api('POST', '/api/appointments', newBooking(this.fixtures)).its('status').should('equal', 201)
    cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
  })

  it('persists note and returns the cancellation reason from history on list and detail', function () {
    cy.api('POST', '/api/appointments', newBooking(this.fixtures, { note: 'ตรวจอาการ\nปวดหลัง' }))
      .then(({ status, body }) => {
        expect(status).to.equal(201)
        cy.api('GET', `/api/appointments/${body.id}`).its('body.note').should('equal', 'ตรวจอาการ\nปวดหลัง')
        cy.api('POST', `/api/appointments/${body.id}/cancel`, { reason: 'คนไข้ขอเลื่อนการรักษา' }).its('status').should('equal', 200)
        cy.api('GET', `/api/appointments/${body.id}`).its('body.cancelReason').should('equal', 'คนไข้ขอเลื่อนการรักษา')
        cy.api('GET', '/api/appointments').then(({ body: page }) => {
          expect(page.items[0].note).to.equal('ตรวจอาการ\nปวดหลัง')
          expect(page.items[0].cancelReason).to.equal('คนไข้ขอเลื่อนการรักษา')
        })
      })
  })

  it('preserves omitted notes and supports editing/clearing notes without a false conflict with its own slot', function () {
    cy.api('POST', '/api/appointments', newBooking(this.fixtures, { note: 'Original' })).then(({ body }) => {
      cy.api('PATCH', `/api/appointments/${body.id}`, { startAt: at(this.fixtures, '10:00') })
        .its('body.note').should('equal', 'Original')
      cy.api('PATCH', `/api/appointments/${body.id}`, { startAt: at(this.fixtures, '10:00'), note: 'Edited' })
        .its('status').should('equal', 200)
      cy.api('GET', `/api/appointments/${body.id}`).its('body.note').should('equal', 'Edited')
      cy.api('PATCH', `/api/appointments/${body.id}`, { startAt: at(this.fixtures, '10:00'), note: null })
        .its('status').should('equal', 200)
      cy.api('GET', `/api/appointments/${body.id}`).its('body.note').should('be.null')
    })
  })

  it('searches CURRENT full name/middle/nickname/phone and NEW name/phone within the authorized branch', function () {
    cy.registerPatient({ firstName: 'สมชาย', middleName: 'กลาง', lastName: 'ใจดี', nickname: 'ชายน้อย' }).then((patient) => {
      cy.api('POST', '/api/appointments', { branchCode: '01', therapistId: this.fixtures.therapistId,
        patientId: patient.id, serviceId: this.fixtures.serviceId, startAt: at(this.fixtures) }).its('status').should('equal', 201)
      cy.api('POST', '/api/appointments', newBooking(this.fixtures, { startAt: at(this.fixtures, '10:00') }))
      cy.api('POST', '/api/appointments', newBooking(this.fixtures, {
        branchCode: '02', therapistId: this.fixtures.therapist2Id, contactName: 'สาขาสอง เท่านั้น', contactPhone: '0822222222',
      })).its('status').should('equal', 201)
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        for (const query of ['สมชาย กลาง ใจดี', 'กลาง', 'ชายน้อย', '0811111111', 'ผู้ป่วย ใหม่', '0891234567']) {
          cy.apiAs(auth.token, 'GET', `/api/appointments?query=${encodeURIComponent(query)}`).then(({ status, body }) => {
            expect(status).to.equal(200)
            expect(body.totalItems, query).to.equal(1)
            expect(body.items[0].branchCode).to.equal('01')
          })
        }
        cy.apiAs(auth.token, 'GET', `/api/appointments?query=${encodeURIComponent('สาขาสอง')}`).its('body.totalItems').should('equal', 0)
      })
    })
  })

  it('applies the query before pagination, including records beyond the first page', function () {
    for (let index = 0; index < 21; index++) {
      cy.api('POST', '/api/appointments', newBooking(this.fixtures, {
        startAt: `${this.fixtures.tomorrow}T09:${String(index).padStart(2, '0')}:00+07:00`,
        contactName: index === 20 ? 'เป้าหมาย หน้าสอง' : `รายการ ${index}`,
      })).its('status').should('equal', 201)
    }
    cy.api('GET', `/api/appointments?query=${encodeURIComponent('เป้าหมาย')}&size=1`).then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      expect(body.totalPages).to.equal(1)
      expect(body.items[0].contactName).to.equal('เป้าหมาย หน้าสอง')
    })
  })

  it('confirms NEW, registers once, links, and attends without creating a duplicate patient', function () {
    cy.api('POST', '/api/appointments', newBooking(this.fixtures, { status: 'Unconfirmed' })).then(({ body }) => {
      cy.api('POST', `/api/appointments/${body.id}/confirm`).its('status').should('equal', 200)
      cy.registerPatient().then((patient) => {
        cy.api('POST', `/api/appointments/${body.id}/link-patient`, { patientId: patient.id }).its('status').should('equal', 200)
        cy.api('GET', `/api/appointments/${body.id}`).then(({ body: linked }) => {
          expect(linked.patientType).to.equal('CURRENT')
          expect(linked.patient.id).to.equal(patient.id)
        })
        for (const action of ['check-in', 'start-service', 'complete']) {
          cy.api('POST', `/api/appointments/${body.id}/${action}`).its('status').should('equal', 200)
        }
        cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 1)
      })
    })
  })

  it('serializes simultaneous links and never silently relinks the appointment', function () {
    cy.api('POST', '/api/appointments', newBooking(this.fixtures)).then(({ body: appointment }) => {
      cy.registerPatient().then((first) => {
        cy.registerPatient({ identityNumber: '1100000000888', firstName: 'อีกคน' }).then((second) => {
          cy.parallelApi([first, second].map((patient) => ({ method: 'POST',
            url: `/api/appointments/${appointment.id}/link-patient`, body: { patientId: patient.id } })))
            .then((results) => {
              expect(results.map((result) => result.status).sort()).to.deep.equal([200, 400])
              const winner = results.find((result) => result.status === 200).body.patient.id
              cy.api('GET', `/api/appointments/${appointment.id}`).its('body.patient.id').should('equal', winner)
              cy.api('POST', `/api/appointments/${appointment.id}/link-patient`,
                { patientId: winner === first.id ? second.id : first.id }).its('status').should('equal', 400)
              cy.api('GET', `/api/appointments/${appointment.id}`).its('body.patient.id').should('equal', winner)
            })
        })
      })
    })
  })
})
