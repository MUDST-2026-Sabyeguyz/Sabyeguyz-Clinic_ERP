const progress = (id) =>
    cy.api('GET', `/api/appointments/${id}/progress`)
        .then(({ status, body }) => {
            expect(status).to.equal(200)
            return body
        })

const sell = (fx, patient, appointment, amount = 800) =>
    cy.api('POST', '/api/payments', {
        patientId: patient.id,
        branchCode: '01',
        appointmentId: appointment.id,
        treatingStaffId: fx.therapistId,
        paymentLabelCode: 'FULL',
        items: [{
            type: 'SERVICE',
            referenceId: fx.singleVisitServiceId,
            quantity: 1,
        }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount }],
    })

function book(fx, patient, type) {
    if (type === 'CURRENT') {
        return cy.createServiceAppointment(fx, patient.id)
    }

    return cy.configureBookingWindows().then(() =>
        cy.api('POST', '/api/appointments', {
            branchCode: '01',
            therapistId: fx.therapistId,
            patientType: 'NEW',
            contactName: 'Progress Contact',
            contactPhone: '0812345678',
            startAt: `${fx.tomorrow}T10:00:00+07:00`,
            endAt: `${fx.tomorrow}T11:00:00+07:00`,
        })
    ).then(({ status, body }) => {
        expect(status).to.equal(201)
        return body
    })
}

describe('v2 appointment progress from committed records', () => {
    for (const type of ['CURRENT', 'NEW']) {
        it(`recovers the saved receipt for ${type} without creating another sale`, function () {
            const fx = this.fixtures

            cy.registerPatient().then((patient) =>
                book(fx, patient, type).then((appointment) => {
                    progress(appointment.id).its('state')
                        .should('equal', 'NO_LINKED_RECORD')

                    sell(fx, patient, appointment, 700)
                        .its('status').should('equal', 400)

                    progress(appointment.id).then((body) => {
                        expect(body.state).to.equal('NO_LINKED_RECORD')
                        expect(body.visitId).to.equal(null)
                        expect(body.sales).to.have.length(0)
                    })

                    sell(fx, patient, appointment).then(({ status, body: sale }) => {
                        expect(status).to.equal(201)

                        progress(appointment.id).then((saved) => {
                            expect(saved.state).to.equal('FOUND')
                            expect(saved.patientType).to.equal(type)
                            expect(saved.appointmentStatus).to.equal('CONFIRMED')
                            expect(saved.visitId).to.be.a('number')
                            expect(saved.servedPatientId).to.equal(patient.id)
                            expect(saved.sales).to.have.length(1)
                            expect(saved.sales[0].saleId).to.equal(sale.id)
                            expect(saved.sales[0].receiptId).to.equal(sale.receiptId)
                            expect(saved.usages).to.have.length(0)

                            // Reloading or retrying must recover the same committed result.
                            progress(appointment.id).should('deep.equal', saved)
                            sell(fx, patient, appointment)
                                .its('status').should('equal', 409)
                            progress(appointment.id).should('deep.equal', saved)
                        })

                        cy.api('GET', `/api/appointments/${appointment.id}`)
                            .then(({ body }) => {
                                expect(body.patientType).to.equal(type)
                                expect(body.patient.id)
                                    .to.equal(type === 'NEW' ? null : patient.id)
                            })
                    })
                })
            )
        })
    }

    it('returns all course usages and retains a reversed usage for review', function () {
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

                    progress(appointment.id).then((body) => {
                        expect(body.state).to.equal('FOUND')
                        expect(body.sales).to.have.length(0)
                        expect(body.usages).to.have.length(2)
                        expect(body.usages.every(
                            (row) => row.coursePurchaseId === course.id
                        )).to.equal(true)

                        cy.api('POST',
                            `/api/course-movements/${body.usages[0].movementId}/reverse`,
                            { reason: 'ทดสอบอ่านรายการที่ย้อนแล้ว' }
                        ).its('status').should('equal', 200)
                    })

                    progress(appointment.id).then((body) => {
                        expect(body.state).to.equal('REVIEW_REQUIRED')
                        expect(body.usages).to.have.length(2)
                        expect(body.usages.map((row) => row.status).sort())
                            .to.deep.equal(['Completed', 'Reversed'])
                    })
                })
            })
        )
    })

    it('allows home-branch access and rejects foreign or anonymous access', function () {
        const fx = this.fixtures

        cy.registerPatient().then((patient) =>
            cy.createServiceAppointment(fx, patient.id).then((appointment) => {
                sell(fx, patient, appointment).its('status').should('equal', 201)

                cy.login('therapist', 'therapist123').then(({ body }) => {
                    cy.apiAs(body.token, 'GET',
                        `/api/appointments/${appointment.id}/progress`
                    ).its('body.state').should('equal', 'FOUND')
                })

                cy.login('therapist2', 'therapist123').then(({ body }) => {
                    cy.apiAs(body.token, 'GET',
                        `/api/appointments/${appointment.id}/progress`
                    ).its('status').should('equal', 403)
                })

                cy.apiAs(null, 'GET',
                    `/api/appointments/${appointment.id}/progress`
                ).its('status').should('equal', 401)
            })
        )
    })
})