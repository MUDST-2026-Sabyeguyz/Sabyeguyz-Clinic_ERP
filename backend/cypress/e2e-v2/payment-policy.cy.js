const sale = (fx, patient, payments, course = false) => {
  const send = (appointmentId) =>
      cy.api('POST', '/api/payments', {
        patientId: patient.id,
        branchCode: '01',
        appointmentId,
        salespersonStaffId: fx.managerStaffId,
        treatingStaffId: fx.managerStaffId,
        paymentLabelCode: 'FULL',
        items: [{type: course ? 'COURSE' : 'SERVICE', referenceId: course ? fx.courseId : fx.singleVisitServiceId, quantity: 1,}],
        payments,
      })
  if (course) {
    return send(undefined)
  }

  return cy.createServiceAppointment(fx, patient.id)
      .then((appointment) => send(appointment.id))
}

const counts = () => cy.api('GET', '/__cypress/payment-policy/counts').its('body')
const refund = (id) => cy.api('POST', `/api/payments/${id}/refunds`, { amount: 100, paymentMethodCode: 'BankTransfer', reason: 'test' })

describe('v2 payment policy', () => {
  it('rejects Cash without creating sale records', function () {
    cy.registerPatient().then(patient => counts().then(before => {
      sale(this.fixtures, patient, [
        { paymentMethodCode: 'Cash', amount: 800 },
      ]).then(({ status }) => {
        expect(status).to.equal(400)
      })

      counts().should('deep.equal', before)
    }))
  })
  for (const amount of [799, 801]) {
    it(`rejects amount ${amount} when receipt net is 800`, function () {
      cy.registerPatient().then(patient => counts().then(before => {
        sale(this.fixtures, patient, [
          { paymentMethodCode: 'PromptPay', amount },
        ]).then(({ status }) => {
          expect(status).to.equal(400)
        })

        counts().should('deep.equal', before)
      }))
    })
  }
  it('rejects mixed methods before leaving any sale records', function () {
    cy.registerPatient().then(patient => counts().then(before => {
      sale(this.fixtures, patient, [{ paymentMethodCode: 'BankTransfer', amount: 400 }, { paymentMethodCode: 'QR', amount: 400 }]).its('status').should('equal', 400)
      counts().should('deep.equal', before)
    }))
  })
  it('rejects multiple payments using the same method', function () {
    cy.registerPatient().then(patient => counts().then(before => {
      sale(this.fixtures, patient, [
        { paymentMethodCode: 'PromptPay', amount: 400 },
        { paymentMethodCode: 'PromptPay', amount: 400 },
      ]).then(({ status }) => {
        expect(status).to.equal(400)
      })

      counts().should('deep.equal', before)
    }))
  })
  for (const method of ['CreditCard', 'PromptPay', 'BankTransfer', 'QR']) {
    it(`accepts ${method} and returns canonical method and persisted label`, function () {
      cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: method, amount: 800 }]).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.paymentLabel).to.equal('FullyPaid')
        cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) => {
          expect(detail.paymentLabel).to.equal('FullyPaid')
          expect(detail.payments[0].paymentMethodCode).to.equal(method === 'QR' ? 'PromptPay' : method)
        })
        cy.api('GET', '/api/payments').its('body.items.0.paymentLabel').should('equal', 'FullyPaid')
      }))
    })
  }
  it('rejects a second completed payment for either method', function () {
    cy.registerPatient().then(patient =>
        sale(this.fixtures, patient, [
          { paymentMethodCode: 'PromptPay', amount: 800 },
        ]).then(({ status, body }) => {
          expect(status).to.equal(201)

          counts().then(before => {
            for (const method of ['PromptPay', 'BankTransfer']) {
              cy.api('POST', `/__cypress/payment-policy/${body.id}/record`, {
                method, amount: 800,
              }).its('status').should('equal', 400)
            }

            counts().should('deep.equal', before)

            cy.api('GET', `/api/receipts/${body.id}`).its('body.payments').should('have.length', 1)
          })
        })
    )
  })
  it('allows only one concurrent completed payment', function () {
    cy.registerPatient().then(patient =>
        sale(this.fixtures, patient, [], true).then(({ status, body }) => {
          expect(status).to.equal(201)

          cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) => {
            expect(detail.payments).to.have.length(0)

            cy.parallelApi(
                ['BankTransfer', 'PromptPay'].map(method => ({
                  method: 'POST',
                  url: `/__cypress/payment-policy/${body.id}/record`,
                  body: { method, amount: detail.netAmount },
                }))
            ).then(responses => {
              expect(responses.map(r => r.status).sort()).to.deep.equal([200, 400])
              cy.api('GET', `/api/receipts/${body.id}`).its('body.payments').should('have.length', 1)
            })
          })
        })
    )
  })
  const courseRefund = (paymentId, courseId, amount, visits, extra = {}) => cy.api('POST', `/api/payments/${paymentId}/refunds`, {
    amount, paymentMethodCode: 'BankTransfer', reason: 'ลูกค้าขอคืนสิทธิ์ที่ไม่ได้ใช้',
    items: [{ courseId, refundAmount: amount, cancelledVisits: visits }], ...extra,
  })
  const usedCourse = (fx, patient, body) => cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) =>
    cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => ({ paymentId: detail.payments[0].id, courseId: courses[0].id })))

  it('refunds only the unused rights of a used course and keeps what was already earned', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'BankTransfer', amount: 9000 }], true).then(({ body }) => {
      usedCourse(this.fixtures, patient, body).then(({ paymentId, courseId }) => {
        cy.useCourseSession(courseId).its('status').should('equal', 200)
        counts().then(before => {
          // a course receipt needs the course and the sessions to cancel; more than what is left is refused
          refund(paymentId).its('status').should('equal', 400)
          courseRefund(paymentId, courseId, 1500, 6).its('status').should('equal', 400)
          cy.api('POST', `/api/payments/${paymentId}/refunds`, {
            amount: 1500, paymentMethodCode: 'BankTransfer', reason: 'x', items: [{ courseId, refundAmount: 1000, cancelledVisits: 5 }],
          }).its('status').should('equal', 400)
          counts().should('deep.equal', before)
          courseRefund(paymentId, courseId, 1500, 4).then(({ status, body: pending }) => {
            expect(status).to.equal(201)
            expect(pending.items).to.deep.equal([{ courseId, refundAmount: 1500, cancelledVisits: 4 }])
            // asking for the rights does not cut them yet
            cy.api('GET', `/api/course-purchases/${courseId}`).its('body.purchase.remaining').should('equal', 4)
            cy.api('POST', `/api/refunds/${pending.id}/complete`).its('status').should('equal', 200)
            cy.api('POST', `/api/refunds/${pending.id}/complete`).its('status').should('equal', 400)
          })
          cy.api('GET', `/api/course-purchases/${courseId}`).then(({ body: course }) => {
            expect(course.purchase.remaining).to.equal(0)
            expect(course.purchase.used).to.equal(1)
            expect(course.purchase.status).to.equal('REFUNDED')
          })
          counts().then(after => {
            expect(after.allocations).to.equal(before.allocations) // the session already given keeps its allocation
            expect(after.refunds).to.equal(before.refunds + 1)
          })
        })
      })
    }))
  })
  it('keeps the course usable when only some rights are refunded', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'BankTransfer', amount: 9000 }], true).then(({ body }) => {
      usedCourse(this.fixtures, patient, body).then(({ paymentId, courseId }) => {
        courseRefund(paymentId, courseId, 3000, 2).then(({ body: pending }) => {
          cy.api('POST', `/api/refunds/${pending.id}/complete`).its('status').should('equal', 200)
        })
        cy.api('GET', `/api/course-purchases/${courseId}`).then(({ body: course }) => {
          expect(course.purchase.remaining).to.equal(3)
          expect(course.purchase.status).to.equal('ACTIVE')
        })
        cy.useCourseSession(courseId).its('status').should('equal', 200)
      })
    }))
  })
  it('does not cut rights for a failed or voided refund request', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'BankTransfer', amount: 9000 }], true).then(({ body }) => {
      usedCourse(this.fixtures, patient, body).then(({ paymentId, courseId }) => {
        courseRefund(paymentId, courseId, 1000, 2).then(({ body: first }) => {
          cy.api('POST', `/api/refunds/${first.id}/fail`).its('body.status').should('equal', 'Failed')
        })
        courseRefund(paymentId, courseId, 1000, 2).then(({ body: second }) => {
          cy.api('POST', `/api/refunds/${second.id}/void`).its('body.status').should('equal', 'Voided')
          cy.api('POST', `/api/refunds/${second.id}/complete`).its('status').should('equal', 400)
        })
        cy.api('GET', `/api/course-purchases/${courseId}`).its('body.purchase.remaining').should('equal', 5)
      })
    }))
  })
  it('rejects completion when usage left too few rights after the refund request', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'BankTransfer', amount: 9000 }], true).then(({ body }) => {
      usedCourse(this.fixtures, patient, body).then(({ paymentId, courseId }) => courseRefund(paymentId, courseId, 9000, 5).then(({ body: pending, status }) => {
        expect(status).to.equal(201)
        cy.useCourseSession(courseId).its('status').should('equal', 200)
        counts().then(before => {
          cy.api('POST', `/api/refunds/${pending.id}/complete`).its('status').should('equal', 409)
          counts().should('deep.equal', before)
          cy.api('GET', `/api/payments/${paymentId}/refunds`).its('body.0.status').should('equal', 'Pending')
          cy.api('GET', `/api/course-purchases/${courseId}`).its('body.purchase.remaining').should('equal', 4)
        })
      }))
    }))
  })
  for (const complete of [false, true]) {
    it(`waits for an in-flight usage before ${complete ? 'completing' : 'requesting'} a refund`, function () {
      cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'BankTransfer', amount: 9000 }], true).then(({ body }) => {
        cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) => {
          const paymentId = detail.payments[0].id
          const prepare = complete ? null : cy.wrap(null)
          cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => (complete
            ? courseRefund(paymentId, courses[0].id, 9000, 5).its('body.id') : cy.wrap(null)).then(refundId => {
            cy.createServiceAppointment(this.fixtures, patient.id)
                .then((appointment) => {
                  return counts().then(before => cy.get('@token').then(token => {
                    return cy.task('courseRefundRace', {
                      token,
                      courseId: courses[0].id,
                      appointmentId: appointment.id,
                      refundUrl: complete
                          ? `/api/refunds/${refundId}/complete`
                          : `/api/payments/${paymentId}/refunds`,
                      refundBody: complete ? undefined : {
                        amount: 9000,
                        paymentMethodCode: 'BankTransfer',
                        reason: 'race test',
                        items: [{
                          courseId: courses[0].id,
                          refundAmount: 9000,
                          cancelledVisits: 5,
                        }],
                      },
                    }, { log: false }).then(result => {
                      expect(
                          result.observedWait,
                          'refund waited on production course row lock'
                      ).to.equal(true)
                      expect(result.usage.status).to.equal(200)
                      expect(result.refund.status).to.equal(complete ? 409 : 400)

                      return counts().then(after => {
                        expect(after.refunds).to.equal(before.refunds)
                        expect(after.cancellations).to.equal(before.cancellations)
                        expect(after.allocations).to.equal(before.allocations)
                      })
                    })
                  }))
                })
          }))
        })
      }))
    })
  }

})
