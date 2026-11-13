const range = '?from=2020-01-01&to=2099-12-31'

describe('v2 reports', () => {
  it('answers every report on an empty clinic', () => {
    for (const path of ['/api/dashboard/summary', '/api/reports/revenue', '/api/reports/appointments',
      '/api/reports/course-balances', '/api/reports/staff-sales', '/api/commission/report']) {
      cy.api('GET', `${path}${range}`).then(({ status, body }) => {
        expect(status, path).to.equal(200)
        expect(body, path).to.exist
      })
    }
  })

  it('counts actual visits by service date and branch even after appointment cancellation', function () {
    const fx = this.fixtures
    cy.registerPatient().then((patient) =>
        cy.createServiceAppointment(fx, patient.id).then((appointment) => {
          cy.api('POST', '/api/payments', {
            patientId: patient.id,
            branchCode: '01',
            appointmentId: appointment.id,
            treatingStaffId: fx.therapistId,
            paymentLabelCode: 'FULL',
            items: [{type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1,}],
            payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
          }).its('status').should('equal', 201)

          cy.api('GET', `/api/reports/revenue${range}`).then(({ body }) => {
            expect(Number(body.totalRevenue)).to.equal(800)
          })

          cy.api('POST', `/api/appointments/${appointment.id}/cancel`, {
            reason: 'ยกเลิกนัดโดยไม่ย้อนบริการที่รับแล้ว',
          }).its('status').should('equal', 200)

          // Midnight in Bangkok: included on October 1, excluded on September 30.
          cy.api('POST', '/__cypress/dashboard/dates', {
            patientId: patient.id,
            visitAt: '2030-09-30T17:00:00Z',
          }).its('status').should('equal', 200)

          cy.api('GET',
              '/api/reports/appointments?from=2030-10-01&to=2030-10-01&branchCode=01'
          ).then(({ status, body }) => {
            expect(status).to.equal(200)
            expect(body.appointments).to.equal(0)
            expect(body.visits).to.equal(1)
          })

          cy.api('GET',
              '/api/reports/appointments?from=2030-09-30&to=2030-09-30&branchCode=01'
          ).its('body.visits').should('equal', 0)

          cy.api('GET',
              '/api/reports/appointments?from=2030-10-01&to=2030-10-01&branchCode=02'
          ).its('body.visits').should('equal', 0)

          cy.api('GET', `/api/reports/appointments${range}&branchCode=01`)
              .then(({ body }) => {
                expect(body.appointments).to.equal(1)
                expect(body.visits).to.equal(1)
                expect(body.byStatus).to.deep.equal([{ status: 'Cancelled', appointments: 1 },])
              })
        })
    )
  })

  it('shows a bought course in the balance report', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
        paymentLabelCode: 'FULL',
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/course-balances${range}`).then(({ body }) => {
        expect(body.purchases).to.equal(1)
        expect(body.rows[0].hn).to.equal(patient.hn)
      })
    })
  })

  it('counts appointments by status', function () {
    cy.registerPatient().then((patient) => {
      cy.configureBookingWindows()
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: patient.id,
        therapistId: this.fixtures.therapistId,
        serviceId: this.fixtures.serviceId,
        startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`,
        endAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/appointments${range}`).then(({ body }) => {
        expect(body.appointments).to.equal(1)
      })
    })
  })

  it('requires authentication', () => {
    cy.apiAs(null, 'GET', `/api/reports/revenue${range}`).its('status').should('equal', 401)
  })

  it('counts two course usages in one Visit as one actual visit', function () {
    const fx = this.fixtures
    cy.registerPatient().then((patient) =>
        cy.api('POST', '/api/course-purchases', {
          patientId: patient.id,
          courseId: fx.courseId,
          branchCode: '01',
          caseOwnerStaffId: fx.therapistId,
          paymentLabelCode: 'FULL',
        }).then(({ status, body: course }) => {
          expect(status).to.equal(201)

          cy.createServiceAppointment(fx, patient.id).then((appointment) => {
            cy.api('POST', '/api/course-usages/batch', {
              usages: Array.from({ length: 2 }, () => ({
                coursePurchaseId: course.id,
                appointmentId: appointment.id,
                patientId: patient.id,
                treatingStaffId: fx.therapistId,
              })),
            }).its('status').should('equal', 200)

            cy.api('GET', `/api/reports/appointments${range}&branchCode=01`)
                .then(({ status, body }) => {
                  expect(status).to.equal(200)
                  expect(body.appointments).to.equal(1)
                  expect(body.visits).to.equal(1)
                })
          })
        })
    )
  })

  it('rejects reversed appointment report dates', () => {
    cy.api('GET',
        '/api/reports/appointments?from=2030-10-02&to=2030-10-01'
    ).its('status').should('equal', 400)
  })
})
