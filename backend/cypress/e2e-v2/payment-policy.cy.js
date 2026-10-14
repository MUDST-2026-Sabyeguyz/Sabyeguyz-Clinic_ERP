const sale = (fx, patient, payments, course = false) => cy.api('POST', '/api/payments', {
  patientId: patient.id, branchCode: '01', salespersonStaffId: fx.managerStaffId,
  items: [{ type: course ? 'COURSE' : 'SERVICE', referenceId: course ? fx.courseId : fx.singleVisitServiceId, quantity: 1 }], payments,
})
const counts = () => cy.api('GET', '/__cypress/payment-policy/counts').its('body')
const refund = (id) => cy.api('POST', `/api/payments/${id}/refunds`, { amount: 100, paymentMethodCode: 'Cash', reason: 'test' })

describe('v2 payment policy', () => {
  it('rejects mixed methods before leaving any sale records', function () {
    cy.registerPatient().then(patient => counts().then(before => {
      sale(this.fixtures, patient, [{ paymentMethodCode: 'Cash', amount: 400 }, { paymentMethodCode: 'QR', amount: 400 }]).its('status').should('equal', 400)
      counts().should('deep.equal', before)
    }))
  })
  for (const method of ['Cash', 'CreditCard', 'PromptPay', 'BankTransfer', 'QR']) {
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
  it('allows repeated canonical records but rejects a different later method', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'QR', amount: 100 }]).then(({ body }) => {
      cy.api('POST', `/__cypress/payment-policy/${body.id}/record`, { method: 'PromptPay', amount: 100 }).its('status').should('equal', 200)
      counts().then(before => {
        cy.api('POST', `/__cypress/payment-policy/${body.id}/record`, { method: 'Cash', amount: 100 }).its('status').should('equal', 400)
        counts().should('deep.equal', before)
      })
    }))
  })
  it('serializes concurrent different methods on an unpaid receipt', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, []).then(({ body }) => {
      cy.parallelApi(['Cash', 'PromptPay'].map(method => ({ method: 'POST', url: `/__cypress/payment-policy/${body.id}/record`, body: { method, amount: 100 } }))).then(responses => {
        expect(responses.map(r => r.status).sort()).to.deep.equal([200, 400])
        cy.api('GET', `/api/receipts/${body.id}`).its('body.payments').should('have.length', 1)
      })
    }))
  })
  it('rejects a refund after course usage without financial side effects', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'Cash', amount: 9000 }], true).then(({ body }) => {
      cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) => {
        cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
          cy.api('POST', '/api/course-usages', { coursePurchaseId: courses[0].id }).its('status').should('equal', 200)
          counts().then(before => {
            refund(detail.payments[0].id).its('status').should('equal', 409)
            counts().should('deep.equal', before)
          })
        })
      })
    }))
  })
  it('rejects completion when usage happened after the refund request', function () {
    cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'Cash', amount: 9000 }], true).then(({ body }) => {
      cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) => refund(detail.payments[0].id).then(({ body: pending, status }) => {
        expect(status).to.equal(201)
        cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
          cy.api('POST', '/api/course-usages', { coursePurchaseId: courses[0].id }).its('status').should('equal', 200)
          counts().then(before => {
            cy.api('POST', `/api/refunds/${pending.id}/complete`).its('status').should('equal', 409)
            counts().should('deep.equal', before)
            cy.api('GET', `/api/payments/${detail.payments[0].id}/refunds`).its('body.0.status').should('equal', 'Pending')
          })
        })
      }))
    }))
  })
  for (const complete of [false, true]) {
    it(`waits for an in-flight usage before ${complete ? 'completing' : 'requesting'} a refund`, function () {
      cy.registerPatient().then(patient => sale(this.fixtures, patient, [{ paymentMethodCode: 'Cash', amount: 9000 }], true).then(({ body }) => {
        cy.api('GET', `/api/receipts/${body.id}`).then(({ body: detail }) => {
          const paymentId = detail.payments[0].id
          const prepare = complete ? refund(paymentId).its('body.id') : cy.wrap(null)
          prepare.then(refundId => cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
            counts().then(before => cy.get('@token').then(token => {
              cy.task('courseRefundRace', {
                token, courseId: courses[0].id,
                refundUrl: complete ? `/api/refunds/${refundId}/complete` : `/api/payments/${paymentId}/refunds`,
                refundBody: complete ? undefined : { amount: 100, paymentMethodCode: 'Cash', reason: 'race test' },
              }, { log: false }).then(result => {
                expect(result.observedWait, 'refund waited on production course row lock').to.equal(true)
                expect(result.usage.status).to.equal(200)
                expect(result.refund.status).to.equal(409)
                counts().then(after => {
                  expect(after.refunds).to.equal(before.refunds)
                  expect(after.cancellations).to.equal(before.cancellations)
                  expect(after.allocations).to.equal(before.allocations + 1)
                })
              })
            }))
          }))
        })
      }))
    })
  }

})
