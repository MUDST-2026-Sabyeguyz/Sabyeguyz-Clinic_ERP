// Phase 2 acceptance: one patient across all steps, without resetting between them.
describe('phase 2 v2 PostgreSQL acceptance', () => {
  it('serves a paid single visit, then uses a course on another appointment for the same patient', function () {
    const fx = this.fixtures
    cy.apiAs(null, 'GET', '/api/patients').its('status').should('equal', 401)
    cy.login('manager', 'wrong-password').its('status').should('equal', 400)
    cy.login('manager', 'manager123').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.role).to.equal('MANAGER')
      expect(body.token).to.be.a('string').and.not.be.empty
      cy.wrap(body.token, { log: false }).as('token')
    })

    cy.registerPatient().then((patient) => {
      expect(patient.hn).to.match(/^\d{2}01\d{2}\d{4}$/)
      cy.api('GET', `/api/patients/${patient.id}`).its('body.hn').should('equal', patient.hn)
      cy.createServiceAppointment(fx, patient.id).then((appointment) => {
        expect(appointment.patient.id).to.equal(patient.id)
        expect(appointment.status).to.equal('UNCONFIRMED')

        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appointment.id,
          treatingStaffId: fx.therapistId,
          paymentLabelCode: 'FULL',
          items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
        }).then(({ status: saleStatus, body: sale }) => {
          expect(saleStatus).to.equal(201)
          expect(sale.receiptNo).to.match(/^RE\d{8}$/)
          expect(Number(sale.total)).to.equal(800)
          expect(Number(sale.paid)).to.equal(800)
          expect(sale.status).to.equal('PAID')
          cy.api('GET', `/api/receipts/${sale.id}`).then(({ status: readStatus, body }) => {
            expect(readStatus).to.equal(200)
            expect(body.patient.id).to.equal(patient.id)
            expect(body.receiptNo).to.equal(sale.receiptNo)
            expect(Number(body.paid)).to.equal(800)
          })
        })

        cy.api('POST', '/api/course-purchases', {
          patientId: patient.id,
          courseId: fx.courseId,
          branchCode: '01',
          caseOwnerStaffId: fx.therapistId,
          paymentLabelCode: 'FULL',
        }).then(({ status: purchaseStatus, body: purchase }) => {
          expect(purchaseStatus).to.equal(201)
          expect(purchase.remaining).to.equal(5)

          cy.createServiceAppointment(fx, patient.id)
              .then((courseAppointment) => {
                cy.useCourseSession(purchase.id, {
                  appointmentId: courseAppointment.id,
                  patientId: patient.id,
                  treatingStaffId: fx.therapistId,
                }).then(({ status: usageStatus, body }) => {
                  expect(usageStatus).to.equal(200)
                  expect(body.used).to.equal(1)
                  expect(body.remaining).to.equal(purchase.remaining - 1)
                })

                cy.api('GET', `/api/appointments/${courseAppointment.id}`)
                    .then(({ status, body }) => {
                      expect(status).to.equal(200)
                      expect(body.status).to.equal('CONFIRMED')
                      expect(body.patient.id).to.equal(patient.id)
                    })
              })

          cy.api('GET', `/api/appointments/${appointment.id}`)
              .its('body.status').should('equal', 'CONFIRMED')

          cy.api('GET', `/api/course-purchases/${purchase.id}`)
              .then(({ body }) => {
                expect(body.purchase.used).to.equal(1)
                expect(body.purchase.remaining).to.equal(purchase.remaining - 1)
                expect(
                    body.movements.filter((movement) => movement.type === 'USAGE')
                ).to.have.length(1)
              })
          cy.api('GET', '/api/course-usages').then(({ body }) => {
            expect(body).to.have.length(1)
            expect(body[0].coursePurchaseId).to.equal(purchase.id)
            expect(body[0].patientHn).to.equal(patient.hn)
            expect(body[0].voidedAt).to.equal(null)
          })
        })
      })
    })
  })
})
