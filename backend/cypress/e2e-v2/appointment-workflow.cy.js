const at = (fx, time = '09:00') => `${fx.tomorrow}T${time}:00+07:00`
function newBooking(fx, overrides = {}) {
  const startAt = overrides.startAt ?? at(fx)
  const endAt = new Date(
      new Date(startAt).getTime() + 60 * 60 * 1000
  ).toISOString()

  return {
    branchCode: '01',
    therapistId: fx.therapistId,
    startAt, endAt,
    patientType: 'NEW',
    contactName: 'ผู้ป่วย ใหม่',
    contactPhone: '0891234567',
    ...overrides,
  }
}

describe('P3 non-commission appointment workflow', () => {
  beforeEach(() => {
    cy.api('GET', '/api/branches').then(({ body }) => {
      body
          .filter((branch) => ['01', '02'].includes(branch.branchCode))
          .forEach((branch) => {
            cy.api('PUT', `/api/branches/${branch.id}`, {
              branchCode: branch.branchCode,
              name: branch.name,
              address: branch.address,
              phone: branch.phone,
              bookingOpenTime: '09:00',
              bookingCloseTime: '18:00',
              defaultAppointmentMinutes: 60,
            }).its('status').should('equal', 200)
          })
    })
  })

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
    cy.api('POST', '/api/appointments',
        newBooking(this.fixtures, { note: 'Original' })
    ).then(({ body }) => {
      const times = {
        startAt: at(this.fixtures, '10:00'),
        endAt: at(this.fixtures, '11:00'),
      }

      cy.api('PATCH', `/api/appointments/${body.id}`, times)
          .its('body.note').should('equal', 'Original')

      cy.api('PATCH', `/api/appointments/${body.id}`, {
        ...times, note: 'Edited',
      }).its('status').should('equal', 200)

      cy.api('GET', `/api/appointments/${body.id}`)
          .its('body.note').should('equal', 'Edited')

      cy.api('PATCH', `/api/appointments/${body.id}`, {
        ...times, note: null,
      }).its('status').should('equal', 200)

      cy.api('GET', `/api/appointments/${body.id}`)
          .its('body.note').should('be.null')
    })
  })

  it('searches CURRENT full name/middle/nickname/phone and NEW name/phone within the authorized branch', function () {
    cy.registerPatient({ firstName: 'สมชาย', middleName: 'กลาง', lastName: 'ใจดี', nickname: 'ชายน้อย' }).then((patient) => {
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        therapistId: this.fixtures.therapistId,
        patientId: patient.id,
        serviceId: this.fixtures.serviceId,
        startAt: at(this.fixtures),
        endAt: at(this.fixtures, '10:00'),
      }).its('status').should('equal', 201)
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
      const start = Date.parse(at(this.fixtures)) + index * 15 * 60 * 1000

      cy.api('POST', '/api/appointments', newBooking(this.fixtures, {
        startAt: new Date(start).toISOString(),
        endAt: new Date(start + 15 * 60 * 1000).toISOString(),
        contactName: index === 20 ? 'เป้าหมาย หน้าสอง' : `รายการ ${index}`,
      })).its('status').should('equal', 201)
    }
    cy.api('GET', `/api/appointments?query=${encodeURIComponent('เป้าหมาย')}&size=1`).then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      expect(body.totalPages).to.equal(1)
      expect(body.items[0].contactName).to.equal('เป้าหมาย หน้าสอง')
    })
  })

  it('serves a registered patient without linking the original NEW appointment', function () {
    cy.api('POST', '/api/appointments',
        newBooking(this.fixtures, { status: 'Unconfirmed' })
    ).then(({ body: appointment }) => {
      cy.registerPatient().then((patient) => {
        cy.api('POST', `/api/appointments/${appointment.id}/link-patient`, {
          patientId: patient.id,
        }).its('status').should('equal', 409)

        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appointment.id,
          treatingStaffId: this.fixtures.therapistId,
          paymentLabelCode: 'FULL',
          items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
          payments: [{paymentMethodCode: 'BankTransfer', amount: 800,}],
        }).then(({ status, body: sale }) => {
          expect(status, JSON.stringify(sale)).to.equal(201)
          expect(sale.patient.id).to.equal(patient.id)
        })

        cy.api('GET', `/api/appointments/${appointment.id}`)
            .then(({ body: unchanged }) => {
              expect(unchanged.status).to.equal('CONFIRMED')
              expect(unchanged.patientType).to.equal('NEW')
              expect(unchanged.patient.id).to.equal(null)
              expect(unchanged.patient.hn).to.equal(null)
            })

        cy.api('GET', '/api/patients')
            .its('body.totalItems').should('equal', 1)
      })
    })
  })

  it('rejects simultaneous attempts to link a NEW appointment', function () {
    cy.api('POST', '/api/appointments', newBooking(this.fixtures))
        .then(({ body: appointment }) => {
          cy.registerPatient().then((first) => {
            cy.registerPatient({
              identityNumber: '1100000000888',
              firstName: 'อีกคน',
            }).then((second) => {
              cy.parallelApi([first, second].map((patient) => ({
                method: 'POST',
                url: `/api/appointments/${appointment.id}/link-patient`,
                body: { patientId: patient.id },
              }))).then((results) => {
                expect(results.map((result) => result.status))
                    .to.deep.equal([409, 409])

                cy.api('GET', `/api/appointments/${appointment.id}`)
                    .then(({ body: unchanged }) => {
                      expect(unchanged.patientType).to.equal('NEW')
                      expect(unchanged.patient.id).to.equal(null)
                      expect(unchanged.patient.hn).to.equal(null)
                    })
              })
            })
          })
        })
  })
})
