// P7 / A1: a transfer moves entitlement on the SAME course. It never creates a second course or lot, never changes
// the expiry, never changes how many visits exist, and never touches commission. The history shows the transfer.
const friendPayload = { firstName: 'ปลายทาง', phone: '0822222222', identityNumber: '1100000000102' }

const buy = (fx, patient) =>
    cy.api('POST', '/api/course-purchases', {
      patientId: patient.id,
      courseId: fx.courseId,
      branchCode: '01',
      paymentLabelCode: 'FULL',
    })
const coursesOf = (patient) => cy.api('GET', `/api/patients/${patient.id}/courses`).its('body')
const transfer = (purchase, friend, quantity) =>
  cy.api('POST', '/api/course-transfers', {
    sourcePurchaseId: purchase.id, destinationPatientId: friend.id, quantity, reason: 'ยกให้เพื่อน',
  })

describe('v2 course transfer lifecycle', () => {
  it('keeps one course with one expiry and an unchanged total after a transfer', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(purchase, friend, 2).its('status').should('equal', 201)
          coursesOf(owner).then((ownerCourses) => {
            coursesOf(friend).then((friendCourses) => {
              expect(ownerCourses, 'owner has one course').to.have.length(1)
              expect(friendCourses, 'receiver has one course').to.have.length(1)
              const [mine] = ownerCourses
              const [theirs] = friendCourses
              expect(theirs.id, 'same course id, no new lot').to.equal(mine.id)
              expect(theirs.id).to.equal(purchase.id)
              expect(theirs.expiresAt, 'same expiry').to.equal(mine.expiresAt)
              expect(theirs.expiresAt).to.equal(purchase.expiresAt)
              expect(mine.remaining + theirs.remaining, 'entitlement is conserved').to.equal(purchase.purchased)
              expect(mine.purchased, 'purchased visits unchanged').to.equal(purchase.purchased)
              expect(mine.used).to.equal(0)
            })
          })
        })
      })
    })
  })

  it('leaves price and commission fields of the course untouched', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: before }) => {
          transfer(before, friend, 2).its('status').should('equal', 201)
          cy.api('GET', `/api/course-purchases/${before.id}`).then(({ status, body }) => {
            expect(status).to.equal(200)
            const after = body.purchase
            expect(Number(after.price)).to.equal(Number(before.price))
            expect(after.commissionStatus).to.equal(before.commissionStatus)
            expect(Number(after.lockedCommissionRate)).to.equal(Number(before.lockedCommissionRate))
            expect(Number(after.totalCourseCommissionPool)).to.equal(Number(before.totalCourseCommissionPool))
            expect(Number(after.commissionAllocationPerVisit)).to.equal(Number(before.commissionAllocationPerVisit))
            expect(after.patient.id, 'the buyer stays the buyer').to.equal(owner.id)
          })
        })
      })
    })
  })

  it('shows the transfer in both patients history with HN and names, and in the course ledger', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(purchase, friend, 2).its('status').should('equal', 201)
          for (const patient of [owner, friend]) {
            cy.api('GET', `/api/course-transfers?patientId=${patient.id}`).then(({ body }) => {
              expect(body, `history of ${patient.id}`).to.have.length(1)
              const [row] = body
              expect(row.courseId).to.equal(purchase.id)
              expect(row.quantity).to.equal(2)
              expect(row.sourcePatientId).to.equal(owner.id)
              expect(row.sourcePatientHn).to.equal(owner.hn)
              expect(row.sourcePatientName).to.be.a('string').and.not.be.empty
              expect(row.destinationPatientId).to.equal(friend.id)
              expect(row.destinationPatientHn).to.equal(friend.hn)
              expect(row.destinationPatientName).to.be.a('string').and.not.be.empty
              expect(row.status).to.match(/complete/i)
            })
          }
          cy.api('GET', `/api/course-purchases/${purchase.id}`).its('body.movements').then((movements) => {
            const types = movements.map((m) => m.type)
            expect(types, 'the ledger records the transfer out and in').to.include.members(['TRANSFER_OUT', 'TRANSFER_IN'])
          })
        })
      })
    })
  })

  it('takes a transferred visit from the receiver, not from the buyer, when the receiver is treated', function () {
    cy.registerPatient().then((owner) => {
      cy.registerPatient(friendPayload).then((friend) => {
        buy(this.fixtures, owner).then(({ body: purchase }) => {
          transfer(purchase, friend, 2).its('status').should('equal', 201)
          cy.configureBookingWindows()
          cy.api('POST', '/api/appointments', {
            branchCode: '01',
            patientId: friend.id,
            therapistId: this.fixtures.therapistId,
            serviceId: this.fixtures.serviceId,
            startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`,
            endAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
          }).then(({ status, body: appointment }) => {
            expect(status, JSON.stringify(appointment)).to.equal(201)
            cy.api('POST', `/api/appointments/${appointment.id}/check-in`).its('status').should('equal', 200)
            cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id, appointmentId: appointment.id })
              .its('status').should('equal', 200)
          })
          coursesOf(friend).its('0.remaining').should('equal', 1)
          coursesOf(owner).its('0.remaining').should('equal', 4)
        })
      })
    })
  })

  it('shows the treating therapist name on the course usage list (R38)', function () {
    cy.registerPatient().then((owner) => {
      buy(this.fixtures, owner).then(({ body: purchase }) => {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200)
        cy.api('GET', '/api/course-usages').its('body').then((rows) => {
          const mine = rows.find((row) => row.coursePurchaseId === purchase.id)
          expect(mine, 'the usage is listed').to.exist
          // The course was sold by the signed-in manager, who is therefore the case owner and treating PT.
          expect(mine.therapistName).to.equal('Manager Demo')
        })
      })
    })
  })

  it('shows the owner HN and name on the course usage list', function () {
    cy.registerPatient().then((owner) => {
      buy(this.fixtures, owner).then(({ body: purchase }) => {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200)
        cy.api('GET', '/api/course-usages').its('body').then((rows) => {
          const mine = rows.find((row) => row.coursePurchaseId === purchase.id)
          expect(mine, 'the usage is listed').to.exist
          expect(mine.patientHn).to.equal(owner.hn)
          expect(mine.patientName).to.be.a('string').and.not.be.empty
        })
      })
    })
  })
})
