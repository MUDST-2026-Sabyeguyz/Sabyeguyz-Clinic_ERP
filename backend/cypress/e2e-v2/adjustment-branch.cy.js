const month = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Bangkok',
    year: 'numeric',
    month: '2-digit',
}).format(new Date())

const [year, monthNumber] = month.split('-').map(Number)
const nextMonth = new Date(Date.UTC(year, monthNumber, 1))
    .toISOString().slice(0, 7)

describe('v2 adjustment source branch', () => {
    it('rejects another payout branch and accepts the course sale branch', function () {
        cy.registerPatient().then((patient) => {
            cy.api('POST', '/api/course-purchases', {
                patientId: patient.id,
                courseId: this.fixtures.courseId,
                branchCode: '01',
                caseOwnerStaffId: this.fixtures.therapistId,
                paymentLabelCode: 'FULL',
            }).then(({ status, body: course }) => {
                expect(status).to.equal(201)

                cy.api('POST', '/api/commission/adjustments', {
                    staffId: this.fixtures.therapistId,
                    courseId: course.id,
                    originalMonth: month,
                    appliedMonth: nextMonth,
                    amount: 50,
                    reason: 'ทดสอบสาขาของค่าตอบแทนต้นทาง',
                }).then(({ status, body: adjustment }) => {
                    expect(status).to.equal(201)

                    cy.api(
                        'POST',
                        `/api/commission/adjustments/${adjustment.id}/approve`,
                    ).its('status').should('equal', 200)

                    cy.api(
                        'POST',
                        `/api/commission/adjustments/${adjustment.id}/applications`,
                        { month: nextMonth, branchCode: '02' },
                    ).its('status').should('equal', 400)

                    cy.api(
                        'POST',
                        `/api/commission/adjustments/${adjustment.id}/applications`,
                        { month: nextMonth, branchCode: '01' },
                    ).its('status').should('equal', 201)
                })
            })
        })
    })
})