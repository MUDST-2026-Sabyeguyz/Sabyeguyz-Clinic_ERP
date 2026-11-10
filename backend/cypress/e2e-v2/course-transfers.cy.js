const friendPayload = { firstName: 'ปลายทาง', phone: '0822222222', identityNumber: '1100000000102' }

function buy(fx, patient) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id, courseId: fx.courseId, branchCode: '01', paymentLabelCode: 'FULL',
  }).then((result) => {
    expect(result.status, JSON.stringify(result.body)).to.equal(201)
    expect(result.body.remaining).to.equal(5)
    return result
  })
}

function transfer(owner, friend, purchase, quantity) {
  return cy.api('POST', '/api/course-transfers', {
    sourcePurchaseId: purchase.id, destinationPatientId: friend.id, quantity, reason: 'ยกให้เพื่อน',
  })
}

describe('v2 course transfers', () => {
  it('transfers visits to another patient and moves the balance', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(owner, friend, purchase, 2).then(({ status, body }) => {
            expect(status).to.equal(201)
            expect(body.quantity).to.equal(2)
            expect(body.sourcePatientId).to.equal(owner.id)
            expect(body.destinationPatientId).to.equal(friend.id)
          })
          cy.api('GET', `/api/patients/${owner.id}/courses`).its('body.0.remaining').should('equal', 3)
          cy.api('GET', `/api/patients/${friend.id}/courses`).its('body.0.remaining').should('equal', 2)
        })
      })
    })
  })

  it('lists the transfer history of a patient', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(owner, friend, purchase, 1).its('status').should('equal', 201)
          cy.api('GET', `/api/course-transfers?patientId=${friend.id}`).its('body').should('have.length', 1)
        })
      })
    })
  })

  it('cannot transfer more visits than the owner holds, or to an unknown patient', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(owner, friend, purchase, 7).its('status').should('be.within', 400, 409)
          transfer(owner, { id: 999999 }, purchase, 1).its('status').should('equal', 400)
        })
      })
    })
  })

  it('reverses a transfer and gives the visits back', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(owner, friend, purchase, 2).then(({ status, body: made }) => {
            expect(status, JSON.stringify(made)).to.equal(201)
            cy.api('POST', `/api/course-transfers/${made.id}/reverse`, { reason: 'โอนผิดคน' })
              .its('status').should('equal', 200)
            cy.api('GET', `/api/patients/${owner.id}/courses`).its('body.0.remaining').should('equal', 5)
          })
        })
      })
    })
  })
})
