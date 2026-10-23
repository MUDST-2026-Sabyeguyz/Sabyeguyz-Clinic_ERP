// Phase 2 acceptance: one patient across all steps, without resetting between them.
describe('phase 2 v2 PostgreSQL acceptance', () => {
  it('logs in, registers, books, sells and uses a course through the same appointment', function () {
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
      cy.configureBookingWindows()
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: patient.id,
        therapistId: fx.therapistId,
        serviceId: fx.serviceId,
        startAt: `${fx.tomorrow}T09:00:00+07:00`,
        endAt: `${fx.tomorrow}T10:00:00+07:00`,
      }).then(({ status, body: appointment }) => {
        expect(status).to.equal(201)
        expect(appointment.patient.id).to.equal(patient.id)
        expect(appointment.status).to.equal('CONFIRMED')

        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
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
          expect(purchase.remaining).to.equal(6)
          cy.api('POST', `/api/appointments/${appointment.id}/check-in`)
            .its('body.status').should('equal', 'ARRIVED')
          cy.api('POST', `/api/appointments/${appointment.id}/start-service`)
            .its('body.status').should('equal', 'IN_SERVICE')
          cy.api('POST', '/api/course-usages', {
            coursePurchaseId: purchase.id, appointmentId: appointment.id,
          }).then(({ status: usageStatus, body }) => {
            expect(usageStatus).to.equal(200)
            expect(body.used).to.equal(1)
            expect(body.remaining).to.equal(5)
          })
          cy.api('POST', `/api/appointments/${appointment.id}/complete`)
            .its('body.status').should('equal', 'COMPLETED')
          cy.api('GET', `/api/appointments/${appointment.id}`).then(({ body }) => {
            expect(body.status).to.equal('COMPLETED')
            expect(body.patient.id).to.equal(patient.id)
          })
          cy.api('GET', `/api/course-purchases/${purchase.id}`).then(({ body }) => {
            expect(body.purchase.used).to.equal(1)
            expect(body.purchase.remaining).to.equal(5)
            expect(body.movements.filter((movement) => movement.type === 'USAGE')).to.have.length(1)
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
