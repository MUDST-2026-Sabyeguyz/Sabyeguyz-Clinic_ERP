const counts = () =>
    cy.api('GET', '/__cypress/payment-policy/counts').then(({ status, body }) => {
        expect(status).to.equal(200)
        return body
    })

const usages = (courseId) =>
    cy.api('GET', `/__cypress/commission/courses/${courseId}/usages`)
        .then(({ status, body }) => {
            expect(status).to.equal(200)
            return body
        })

const sell = (fx, context, appointment, quantity) =>
    cy.api('POST', '/api/payments', {
        patientId: context.patient.id,
        branchCode: '01',
        appointmentId: appointment.id,
        treatingStaffId: fx.therapistId,
        paymentLabelCode: 'FULL',
        items: [{
            type: 'SERVICE',
            referenceId: fx.singleVisitServiceId,
            quantity: 1,
        }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
        courseUsages: Array.from({ length: quantity }, () => ({
            coursePurchaseId: context.course.id,
            note: 'ใช้คอร์สร่วมบริการรายครั้ง',
        })),
    })

describe('v2 single-visit service and course usages in one Visit', () => {
    beforeEach(function () {
        cy.registerPatient().then((patient) =>
            cy.api('POST', '/api/course-purchases', {
                patientId: patient.id,
                courseId: this.fixtures.courseId,
                branchCode: '01',
                caseOwnerStaffId: this.fixtures.therapistId,
                paymentLabelCode: 'FULL',
            }).then(({ status, body: course }) => {
                expect(status).to.equal(201)
                cy.wrap({ patient, course }).as('mixed')
            })
        )
    })

    it('shares one Visit and does not charge or deduct again on replay', function () {
        const context = this.mixed

        cy.createServiceAppointment(this.fixtures, context.patient.id)
            .then((appointment) => {
                sell(this.fixtures, context, appointment, 2)
                    .then(({ status, body: sale }) => {
                        expect(status).to.equal(201)

                        cy.api('GET', `/api/receipts/${sale.id}`).then(({ body }) => {
                            expect(body.status).to.equal('PAID')
                            expect(body.items).to.have.length(1)
                            expect(body.payments).to.have.length(1)
                            expect(Number(body.total)).to.equal(800)
                        })
                    })

                usages(context.course.id).then((rows) => {
                    expect(rows).to.have.length(2)
                    expect(rows[0].visit_id).to.be.a('number')
                    expect(rows[1].visit_id).to.equal(rows[0].visit_id)

                    cy.api('GET', '/__cypress/commission/single-visit-fees')
                        .then(({ status, body: fees }) => {
                            expect(status).to.equal(200)
                            expect(fees).to.have.length(1)
                            expect(fees[0].visit_id).to.equal(rows[0].visit_id)
                        })
                })

                cy.api('GET', `/api/course-purchases/${context.course.id}`)
                    .its('body.purchase.remaining')
                    .should('equal', context.course.remaining - 2)

                cy.api('GET', `/api/appointments/${appointment.id}`)
                    .its('body.status').should('equal', 'CONFIRMED')

                counts().then((before) => {
                    sell(this.fixtures, context, appointment, 2)
                        .its('status').should('equal', 409)
                    counts().should('deep.equal', before)
                    usages(context.course.id).should('have.length', 2)
                })

                cy.api('GET', '/api/reports/appointments?from=2020-01-01&to=2099-12-31')
                    .its('body.visits').should('equal', 1)
            })
    })

    it('rolls back payment, usages and Treatments when course rights are insufficient', function () {
        const context = this.mixed

        cy.createServiceAppointment(this.fixtures, context.patient.id)
            .then((appointment) =>
                counts().then((before) => {
                    sell(this.fixtures, context, appointment, context.course.remaining + 1)
                        .its('status').should('equal', 400)

                    counts().should('deep.equal', before)
                    usages(context.course.id).should('have.length', 0)

                    cy.api('GET', `/api/treatments/patients/${context.patient.id}`)
                        .its('body').should('have.length', 0)

                    cy.api('GET', `/api/course-purchases/${context.course.id}`)
                        .its('body.purchase.remaining')
                        .should('equal', context.course.remaining)

                    cy.api('GET', `/api/appointments/${appointment.id}`)
                        .its('body.status').should('equal', 'UNCONFIRMED')

                    cy.api('GET', '/api/reports/appointments?from=2020-01-01&to=2099-12-31')
                        .its('body.visits').should('equal', 0)

                    // The failed transaction must leave the appointment usable.
                    sell(this.fixtures, context, appointment, 1)
                        .its('status').should('equal', 201)
                })
            )
    })
})