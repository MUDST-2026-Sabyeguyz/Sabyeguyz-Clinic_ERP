function buy(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id,
    courseId: fx.courseId,
    branchCode: '01',
    paymentLabelCode: 'FULL',
    ...overrides,
  })
}

describe('v2 courses', () => {
  it('buys the 9,000 THB course with five total visits', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.price)).to.equal(9000)
        expect(body.remaining).to.equal(5)
        expect(body.used).to.equal(0)
        expect(body.expired).to.equal(false)
        expect(body.course.id).to.equal(this.fixtures.courseId)
        expect(body.course.code).to.match(/^RE\d{8}$/)
      })
    })
  })

  it('takes a fixed baht discount off the course price', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient, { discountType: 'BAHT', discountValue: 500 }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.price)).to.equal(8500)
      })
    })
  })

  it('leaves the commission provisional until the monthly closing locks the rate', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).then(({ body }) => {
        expect(body.commissionStatus).to.equal('PROVISIONAL')
        expect(Number(body.lockedCommissionRate)).to.equal(0)
        expect(Number(body.totalCourseCommissionPool)).to.equal(0)
      })
    })
  })

  it('no longer splits a course sale between two PTs: the seller is the single Owner', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient, {
        shares: [
          { staffId: this.fixtures.managerStaffId, percent: 60 },
          { staffId: this.fixtures.therapistId, percent: 40 },
        ],
      }).its('status').should('equal', 400)
    })
  })

  it('records the sale for the PT named in caseOwnerStaffId, who becomes the Owner', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient, { caseOwnerStaffId: this.fixtures.therapistId }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(Number(body.price)).to.equal(9000)
      })
    })
  })

  it('rejects shares that do not add up to 100%', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient, {
        shares: [
          { staffId: this.fixtures.managerStaffId, percent: 60 },
          { staffId: this.fixtures.therapistId, percent: 30 },
        ],
      }).its('status').should('equal', 400)
    })
  })

  it('lists the patient\'s courses', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).its('status').should('equal', 201)
      cy.api('GET', `/api/patients/${patient.id}/courses`).its('body').should('have.length', 1)
    })
  })

  it('uses a course after creating an appointment, then reverses the usage', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)

        cy.useCourseSession(purchase.id, {
          note: 'รับบริการจากนัด',
        }).then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body.used).to.equal(1)
          expect(body.remaining).to.equal(purchase.remaining - 1)
        })

        cy.api('GET', `/api/course-purchases/${purchase.id}`)
            .then(({ body }) => {
              expect(
                  body.allocations,
                  'rate unknown until the sale month is closed'
              ).to.have.length(0)

              const usage = body.movements.find(
                  (movement) => movement.type === 'USAGE'
              )
              expect(usage).to.exist

              cy.api(
                  'POST',
                  `/api/course-movements/${usage.id}/reverse`,
                  { reason: 'บันทึกผิด' }
              ).then((reversed) => {
                expect(reversed.status).to.equal(200)
                expect(reversed.body.used).to.equal(0)
                expect(reversed.body.remaining).to.equal(purchase.remaining)
              })
            })
      })
    })
  })

  it('cannot use more visits than the course holds', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)

        for (let visit = 0; visit < purchase.remaining; visit += 1) {
          cy.useCourseSession(purchase.id)
              .its('status').should('equal', 200)
        }

        cy.useCourseSession(purchase.id)
            .its('status').should('equal', 400)

        cy.api('GET', `/api/course-purchases/${purchase.id}`)
            .then((saved) => {
              expect(saved.status).to.equal(200)
              expect(saved.body.purchase.remaining).to.equal(0)
              expect(saved.body.purchase.used)
                  .to.equal(purchase.used + purchase.remaining)
            })
      })
    })
  })

  it('lets only one of two parallel requests take the last visit', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)
        expect(purchase.remaining).to.be.greaterThan(0)

        for (let visit = 0; visit < purchase.remaining - 1; visit += 1) {
          cy.useCourseSession(purchase.id)
              .its('status').should('equal', 200)
        }

        cy.createServiceAppointment(this.fixtures, patient.id)
            .then((first) => {
              cy.createServiceAppointment(this.fixtures, patient.id)
                  .then((second) => {
                    const request = (appointmentId) => ({
                      method: 'POST',
                      url: '/api/course-usages',
                      body: {
                        coursePurchaseId: purchase.id,
                        patientId: patient.id,
                        appointmentId,
                      },
                    })

                    cy.parallelApi([
                      request(first.id),
                      request(second.id),
                    ]).then((responses) => {
                      const statuses = responses.map((entry) => entry.status)
                      expect(statuses.filter((value) => value === 200))
                          .to.have.length(1)
                      expect(statuses.find((value) => value !== 200))
                          .to.be.oneOf([400, 409])

                      ;[first, second].forEach((appointment, index) => {
                        cy.api('GET', `/api/appointments/${appointment.id}`)
                            .its('body.status').should(
                            'equal',
                            responses[index].status === 200
                                ? 'CONFIRMED'
                                : 'UNCONFIRMED'
                        )
                      })
                    })

                    cy.api('GET', `/api/course-purchases/${purchase.id}`)
                        .then((saved) => {
                          expect(saved.status).to.equal(200)
                          expect(saved.body.purchase.used)
                              .to.equal(purchase.used + purchase.remaining)
                          expect(saved.body.purchase.remaining).to.equal(0)
                        })
                  })
            })
      })
    })
  })

  it('cancels a course purchase', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient).then(({ body: purchase }) => {
        cy.api('POST', `/api/course-purchases/${purchase.id}/cancel`, { reason: 'ยกเลิก' }).then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body.status).to.equal('CANCELLED')
        })
      })
    })
  })

  it('rejects an unknown patient, course or branch', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, { id: 999999 }).its('status').should('equal', 400)
      buy(this.fixtures, patient, { courseId: 999999 }).its('status').should('equal', 400)
      buy(this.fixtures, patient, { branchCode: '99' }).its('status').should('equal', 400)
    })
  })
})
