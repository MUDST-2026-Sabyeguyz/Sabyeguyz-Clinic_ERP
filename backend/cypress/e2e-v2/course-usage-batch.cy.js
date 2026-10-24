const usageRows = (courseId) =>
    cy.api('GET', `/__cypress/commission/courses/${courseId}/usages`)
        .then(({ status, body }) => {
            expect(status).to.equal(200)
            return body
        })

const buy = (fx, patient) =>
    cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: fx.courseId,
        branchCode: '01',
        caseOwnerStaffId: fx.therapistId,
        paymentLabelCode: 'FULL',
    }).then(({ status, body }) => {
        expect(status).to.equal(201)
        return body
    })

const batch = (fx, patient, course, appointment, count) =>
    cy.api('POST', '/api/course-usages/batch', {
        usages: Array.from({ length: count }, () => ({
            coursePurchaseId: course.id,
            appointmentId: appointment.id,
            patientId: patient.id,
            treatingStaffId: fx.therapistId,
        })),
    })

describe('v2 multiple course usages in one Visit', () => {
    it('records two usages in one Visit and rejects replay without deducting again', function () {
        const fx = this.fixtures

        cy.registerPatient().then((patient) =>
            buy(fx, patient).then((course) =>
                cy.createServiceAppointment(fx, patient.id).then((appointment) => {
                    batch(fx, patient, course, appointment, 2)
                        .then(({ status, body }) => {
                            expect(status).to.equal(200)
                            expect(body).to.have.length(1)
                            expect(body[0].used).to.equal(2)
                            expect(body[0].remaining).to.equal(course.remaining - 2)
                        })

                    usageRows(course.id).then((rows) => {
                        expect(rows).to.have.length(2)
                        expect(rows[0].visit_id).to.be.a('number')
                        expect(rows[1].visit_id).to.equal(rows[0].visit_id)
                        expect(rows.map((row) => row.usage_sequence))
                            .to.deep.equal([1, 2])
                    })

                    cy.api('GET', `/api/appointments/${appointment.id}`)
                        .its('body.status').should('equal', 'CONFIRMED')

                    batch(fx, patient, course, appointment, 2)
                        .its('status').should('equal', 409)

                    usageRows(course.id).should('have.length', 2)

                    cy.api('GET', `/api/course-purchases/${course.id}`)
                        .its('body.purchase.remaining')
                        .should('equal', course.remaining - 2)
                })
            )
        )
    })

    it('rolls back every usage when the batch exceeds remaining rights', function () {
        const fx = this.fixtures

        cy.registerPatient().then((patient) =>
            buy(fx, patient).then((course) =>
                cy.createServiceAppointment(fx, patient.id).then((appointment) => {
                    batch(fx, patient, course, appointment, course.remaining + 1)
                        .its('status').should('equal', 400)

                    usageRows(course.id).should('have.length', 0)

                    cy.api('GET', `/api/course-purchases/${course.id}`)
                        .then(({ body }) => {
                            expect(body.purchase.used).to.equal(0)
                            expect(body.purchase.remaining).to.equal(course.remaining)
                        })

                    cy.api('GET', `/api/appointments/${appointment.id}`)
                        .its('body.status').should('equal', 'UNCONFIRMED')

                    // The same appointment remains usable after the failed transaction.
                    batch(fx, patient, course, appointment, 1)
                        .its('status').should('equal', 200)

                    usageRows(course.id).should('have.length', 1)
                })
            )
        )
    })
    
    it('uses two courses in one Visit and rejects inconsistent patient details', function () {
        const fx = this.fixtures

        cy.registerPatient().then((patient) =>
            buy(fx, patient).then((first) =>
                buy(fx, patient).then((second) =>
                    cy.createServiceAppointment(fx, patient.id).then((appointment) => {
                        const items = [first, second].map((course) => ({
                            coursePurchaseId: course.id,
                            appointmentId: appointment.id,
                            patientId: patient.id,
                            treatingStaffId: fx.therapistId,
                        }))

                        cy.api('POST', '/api/course-usages/batch', {
                            usages: [
                                items[0],
                                { ...items[1], patientId: null },
                            ],
                        }).its('status').should('equal', 400)

                        usageRows(first.id).should('have.length', 0)
                        usageRows(second.id).should('have.length', 0)

                        cy.api('GET', `/api/appointments/${appointment.id}`)
                            .its('body.status').should('equal', 'UNCONFIRMED')

                        cy.api('POST', '/api/course-usages/batch', {
                            usages: items,
                        }).then(({ status, body }) => {
                            expect(status).to.equal(200)
                            expect(body).to.have.length(2)

                            for (const course of [first, second]) {
                                const result = body.find((row) => row.id === course.id)
                                expect(result.used).to.equal(1)
                                expect(result.remaining).to.equal(course.remaining - 1)
                            }
                        })

                        usageRows(first.id).then((firstRows) =>
                            usageRows(second.id).then((secondRows) => {
                                expect(firstRows).to.have.length(1)
                                expect(secondRows).to.have.length(1)
                                expect(firstRows[0].visit_id).to.be.a('number')
                                expect(secondRows[0].visit_id)
                                    .to.equal(firstRows[0].visit_id)
                            })
                        )

                        cy.api('GET', `/api/appointments/${appointment.id}`)
                            .its('body.status').should('equal', 'CONFIRMED')
                    })
                )
            )
        )
    })
})