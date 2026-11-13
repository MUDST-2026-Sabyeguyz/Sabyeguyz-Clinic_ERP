const at = (fx, time) => `${fx.tomorrow}T${time}:00+07:00`

function book(fx, patient, overrides = {}) {
  const startAt = overrides.startAt ?? at(fx, '09:00')
  const endAt = new Date(
      new Date(startAt).getTime() + 60 * 60 * 1000
  ).toISOString()

  return cy.api('POST', '/api/appointments', {
    branchCode: '01',
    patientId: patient.id,
    therapistId: fx.therapistId,
    serviceId: fx.serviceId,
    startAt, endAt,
    ...overrides,
  })
}

describe('v2 appointments', () => {
  it('books a registered patient as Unconfirmed with patient, therapist and service in the response', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.status).to.equal('UNCONFIRMED')
        expect(body.patientType).to.equal('CURRENT')
        expect(body.patient.id).to.equal(patient.id)
        expect(body.patient.hn).to.equal(patient.hn)
        expect(body.therapist.id).to.equal(this.fixtures.therapistId)
        expect(body.service.id).to.equal(this.fixtures.serviceId)
      })
    })
  })

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
      book(this.fixtures, patient, { branchCode: '02', therapistId: this.fixtures.therapist2Id }).its('status').should('equal', 201)
    })
  })

  it('keeps a NEW appointment Unconfirmed when direct confirmation is rejected', function () {
    cy.api('POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.therapistId,
      startAt: at(this.fixtures, '10:00'),
      endAt: at(this.fixtures, '11:00'),
      status: 'Unconfirmed',
      patientType: 'NEW',
      contactName: 'สมศรี ใจงาม',
      contactPhone: '0899999999',
    }).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.status).to.equal('UNCONFIRMED')
      expect(body.patientType).to.equal('NEW')
      expect(body.contactName).to.equal('สมศรี ใจงาม')

      cy.api('POST', `/api/appointments/${body.id}/confirm`)
          .its('status').should('equal', 409)

      cy.api('GET', `/api/appointments/${body.id}`).then((saved) => {
        expect(saved.status).to.equal(200)
        expect(saved.body.status).to.equal('UNCONFIRMED')
        expect(saved.body.patientType).to.equal('NEW')
        expect(saved.body.patient.id).to.equal(null)
      })
    })
  })

  it('keeps a NEW appointment unlinked after patient registration', function () {
    cy.api('POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.therapistId,
      startAt: at(this.fixtures, '10:30'),
      endAt: at(this.fixtures, '11:30'),
      patientType: 'NEW',
      contactName: 'สมศรี ใจงาม',
      contactPhone: '0899999999',
    }).then(({ body }) => {
      cy.registerPatient({ firstName: 'สมศรี', lastName: 'ใจงาม', phone: '0899999999' }).then((patient) => {
        cy.api('POST', `/api/appointments/${body.id}/link-patient`, {
          patientId: patient.id,
        }).its('status').should('equal', 409)

        cy.api('GET', `/api/appointments/${body.id}`).then(({ body: unchanged }) => {
          expect(unchanged.patientType).to.equal('NEW')
          expect(unchanged.patient.id).to.equal(null)
          expect(unchanged.patient.hn).to.equal(null)
        })
      })
    })
  })

  it('rejects check-in before successful service and keeps the appointment Unconfirmed', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ status, body }) => {
        expect(status).to.equal(201)

        cy.api('POST', `/api/appointments/${body.id}/check-in`)
            .its('status').should('equal', 409)

        cy.api('GET', `/api/appointments/${body.id}`).then((saved) => {
          expect(saved.status).to.equal(200)
          expect(saved.body.status).to.equal('UNCONFIRMED')
          expect(saved.body.patient.id).to.equal(patient.id)
        })
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

  it('rejects no-show and allows cancellation with a reason', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ status, body }) => {
        expect(status).to.equal(201)

        cy.api('POST', `/api/appointments/${body.id}/no-show`)
            .its('status').should('equal', 409)

        cy.api('GET', `/api/appointments/${body.id}`)
            .its('body.status').should('equal', 'UNCONFIRMED')

        cy.api('POST', `/api/appointments/${body.id}/cancel`, {
          reason: 'ผู้ป่วยไม่มาตามนัด',
        }).then((cancelled) => {
          expect(cancelled.status).to.equal(200)
          expect(cancelled.body.status).to.equal('CANCELLED')
          expect(cancelled.body.cancelReason)
              .to.equal('ผู้ป่วยไม่มาตามนัด')
        })
      })
    })
  })

  it('confirms a CURRENT appointment only after successful payment', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient, {
        serviceId: this.fixtures.singleVisitServiceId,
      }).then(({ status, body: appointment }) => {
        expect(status).to.equal(201)
        expect(appointment.status).to.equal('UNCONFIRMED')

        const payment = {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appointment.id,
          treatingStaffId: this.fixtures.therapistId,
          paymentLabelCode: 'FULL',
          items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
          payments: [{paymentMethodCode: 'BankTransfer', amount: 800,}],
        }

        cy.api('POST', '/api/payments', payment).then((paid) => {
          expect(paid.status).to.equal(201)
          expect(paid.body.status).to.equal('PAID')
          expect(Number(paid.body.paid)).to.equal(800)
        })

        cy.api('GET', `/api/appointments/${appointment.id}`)
            .then((saved) => {
              expect(saved.status).to.equal(200)
              expect(saved.body.status).to.equal('CONFIRMED')
              expect(saved.body.patient.id).to.equal(patient.id)
            })

        // Sending the same request again must not create another sale.
        cy.api('POST', '/api/payments', payment)
            .its('status').should('equal', 409)
      })
    })
  })

  it('keeps the appointment Unconfirmed after failed payment and allows a retry', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient, {
        serviceId: this.fixtures.singleVisitServiceId,
      }).then(({ status, body: appointment }) => {
        expect(status).to.equal(201)

        const payment = {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appointment.id,
          treatingStaffId: this.fixtures.therapistId,
          paymentLabelCode: 'FULL',
          items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
          payments: [{paymentMethodCode: 'BankTransfer', amount: 799,}],
        }

        cy.api('POST', '/api/payments', payment)
            .its('status').should('equal', 400)

        cy.api('GET', `/api/appointments/${appointment.id}`)
            .its('body.status').should('equal', 'UNCONFIRMED')

        cy.api('POST', '/api/payments', {
          ...payment,
          payments: [{paymentMethodCode: 'BankTransfer', amount: 800,
          }],
        }).then((paid) => {
          expect(paid.status).to.equal(201)
          expect(paid.body.status).to.equal('PAID')
        })

        cy.api('GET', `/api/appointments/${appointment.id}`)
            .its('body.status').should('equal', 'CONFIRMED')
      })
    })
  })

  it('rejects course usage without an appointment and preserves remaining rights', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: this.fixtures.courseId,
        branchCode: '01',
        caseOwnerStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
      }).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)

        cy.api('POST', '/api/course-usages', {
          coursePurchaseId: purchase.id,
          patientId: patient.id,
          treatingStaffId: this.fixtures.therapistId,
        }).then((rejected) => {
          expect(rejected.status).to.equal(400)
          expect(rejected.body.message).to.include('appointmentId')
        })

        cy.api('GET', `/api/course-purchases/${purchase.id}`)
            .then((saved) => {
              expect(saved.status).to.equal(200)
              expect(saved.body.purchase.remaining)
                  .to.equal(purchase.remaining)
              expect(saved.body.purchase.used)
                  .to.equal(purchase.used)
            })

        cy.api('GET', '/api/appointments')
            .its('body.totalItems').should('equal', 0)
      })
    })
  })

  it('confirms an appointment after deducting one course session', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: this.fixtures.courseId,
        branchCode: '01',
        caseOwnerStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
      }).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)

        book(this.fixtures, patient).then(({ status, body: appointment }) => {
          expect(status).to.equal(201)
          expect(appointment.status).to.equal('UNCONFIRMED')

          const usage = {
            coursePurchaseId: purchase.id,
            appointmentId: appointment.id,
            patientId: patient.id,
            treatingStaffId: this.fixtures.therapistId,
          }

          // Invalid treatment: the request must roll back.
          cy.api('POST', '/api/course-usages', {
            ...usage,
            treatmentId: 999999,
          }).its('status').should('equal', 400)

          cy.api('GET', `/api/appointments/${appointment.id}`)
              .its('body.status').should('equal', 'UNCONFIRMED')

          cy.api('GET', `/api/course-purchases/${purchase.id}`)
              .then((saved) => {
                expect(saved.status).to.equal(200)
                expect(saved.body.purchase.remaining).to.equal(purchase.remaining)
                expect(saved.body.purchase.used).to.equal(purchase.used)
              })

          cy.api('POST', '/api/course-usages', usage).then((used) => {
            expect(used.status).to.equal(200)
            expect(used.body.remaining).to.equal(purchase.remaining - 1)
            expect(used.body.used).to.equal(purchase.used + 1)
          })

          cy.api('GET', `/api/appointments/${appointment.id}`)
              .its('body.status').should('equal', 'CONFIRMED')

          cy.api('POST', '/api/course-usages', usage)
              .its('status').should('equal', 409)

          cy.api('GET', `/api/course-purchases/${purchase.id}`)
              .then((saved) => {
                expect(saved.status).to.equal(200)
                expect(saved.body.purchase.remaining).to.equal(purchase.remaining - 1)
                expect(saved.body.purchase.used).to.equal(purchase.used + 1)
              })
        })
      })
    })
  })

  it('confirms a NEW appointment after payment without linking the registered patient', function () {
    cy.api('POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.therapistId,
      serviceId: this.fixtures.singleVisitServiceId,
      startAt: at(this.fixtures, '10:00'),
      endAt: at(this.fixtures, '11:00'),
      patientType: 'NEW',
      contactName: 'สมศรี ใจงาม',
      contactPhone: '0899999999',
    }).then(({ status, body: appointment }) => {
      expect(status).to.equal(201)
      expect(appointment.status).to.equal('UNCONFIRMED')

      cy.registerPatient({
        firstName: 'สมศรี',
        lastName: 'ใจงาม',
        phone: '0899999999',
      }).then((patient) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appointment.id,
          treatingStaffId: this.fixtures.therapistId,
          paymentLabelCode: 'FULL',
          items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
          payments: [{paymentMethodCode: 'BankTransfer', amount: 800,}],
        }).its('status').should('equal', 201)

        cy.api('GET', `/api/appointments/${appointment.id}`)
            .then((saved) => {
              expect(saved.status).to.equal(200)
              expect(saved.body.status).to.equal('CONFIRMED')
              expect(saved.body.patientType).to.equal('NEW')
              expect(saved.body.patient.id).to.equal(null)
              expect(saved.body.patient.hn).to.equal(null)
            })
      })
    })
  })

  it('reschedules to a new time', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then(({ body }) => {
        cy.api('PATCH', `/api/appointments/${body.id}`, {
          startAt: at(this.fixtures, '14:00'),
          endAt: at(this.fixtures, '15:00'),
        }).then((r) => {
          expect(r.status).to.equal(200)
          expect(r.body.status).to.equal('UNCONFIRMED')
          expect(new Date(r.body.startAt).toISOString())
              .to.equal(new Date(at(this.fixtures, '14:00')).toISOString())
          expect(new Date(r.body.endAt).toISOString())
              .to.equal(new Date(at(this.fixtures, '15:00')).toISOString())
        })
      })
    })
  })

  it('lists and filters appointments by date, therapist and status', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).its('status').should('equal', 201)
      book(this.fixtures, patient, { branchCode: '02', therapistId: this.fixtures.therapist2Id }).its('status').should('equal', 201)
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
      branchCode: '01',
      patientId: 999999,
      therapistId: this.fixtures.therapistId,
      serviceId: this.fixtures.serviceId,
      startAt: at(this.fixtures, '09:00'),
      endAt: at(this.fixtures, '10:00'),
    }).its('status').should('equal', 400)
  })
})
