/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const buy = (fx, patient) => cy.api('POST', '/api/course-purchases', {
  patientId: patient.id,
  courseId: fx.courseId,
  branchCode: '01',
  caseOwnerStaffId: fx.therapistId,
  paymentLabelCode: 'FULL',
}).then(({ status, body }) => {
  expect(status).to.be.oneOf([200, 201])
  return body
})
const expiryOf = (patient, courseId) => cy.api('GET', `/api/patients/${patient.id}/courses`)
  .then(({ body }) => body.find((course) => course.id === courseId).expiresAt.slice(0, 10))
const ask = (course, extra = {}) => cy.api('POST', '/api/course-extensions', {
  courseId: course.id, value: 2, unit: 'Month', reason: 'ผู้ป่วยเดินทาง', ...extra,
})

describe('v2 course extension: the owner asks, a Manager approves', () => {
  it('creates a pending request that does not move the expiry yet', function () {
    cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
      expiryOf(owner, course.id).then((before) => {
        ask(course).then(({ status, body }) => {
          expect(status).to.equal(201)
          expect(body).to.include({ courseId: course.id, value: 2, unit: 'Month', status: 'Pending' })
          expect(body.requestedPatientId).to.equal(owner.id)
        })
        expiryOf(owner, course.id).should('equal', before)
      })
    }))
  })

  it('allows only one open request per course', function () {
    cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
      ask(course).its('status').should('equal', 201)
      ask(course).its('status').should('equal', 409)
    }))
  })

  it('moves the expiry by months when the Manager approves, and keeps what changed', function () {
    cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
      expiryOf(owner, course.id).then((before) => {
        ask(course).then(({ body: request }) => {
          cy.api('POST', `/api/course-extensions/${request.id}/approve`, { note: 'อนุมัติ' }).then(({ status, body }) => {
            expect(status).to.equal(200)
            expect(body.status).to.equal('Approved')
            expect(body.oldExpiry).to.equal(before)
            const expected = new Date(`${before}T00:00:00Z`)
            expected.setUTCMonth(expected.getUTCMonth() + 2)
            expect(body.newExpiry).to.equal(expected.toISOString().slice(0, 10))
          })
        })
        expiryOf(owner, course.id).should('not.equal', before)
        cy.api('GET', `/api/course-extensions?courseId=${course.id}`).its('body.0.status').should('equal', 'Approved')
      })
    }))
  })

  it('gives everyone who holds the course the same new expiry date', function () {
    cy.registerPatient().then((owner) => cy.registerPatient({
      identityNumber: '1100000000102', username: 'cypress-patient-2', email: 'cypress-patient-2@example.com', phone: '0822222222',
    }).then((member) => buy(this.fixtures, owner).then((course) => {
      cy.api('POST', '/api/course-transfers', {
        sourcePurchaseId: course.id, destinationPatientId: member.id, quantity: 1, reason: 'แชร์ให้ครอบครัว',
      }).its('status').should('equal', 201)
      ask(course, { value: 30, unit: 'Day' }).then(({ body: request }) => {
        cy.api('POST', `/api/course-extensions/${request.id}/approve`, {}).its('status').should('equal', 200)
      })
      expiryOf(owner, course.id).then((ownerExpiry) => expiryOf(member, course.id).should('equal', ownerExpiry))
    })))
  })

  it('refuses a request made on behalf of someone the course was only shared with', function () {
    cy.registerPatient().then((owner) => cy.registerPatient({
      identityNumber: '1100000000102', username: 'cypress-patient-2', email: 'cypress-patient-2@example.com', phone: '0822222222',
    }).then((member) => buy(this.fixtures, owner).then((course) => {
      cy.api('POST', '/api/course-transfers', {
        sourcePurchaseId: course.id, destinationPatientId: member.id, quantity: 1, reason: 'แชร์',
      }).its('status').should('equal', 201)
      ask(course, { requestedByPatientId: member.id }).its('status').should('equal', 403)
      ask(course, { requestedByPatientId: owner.id }).its('status').should('equal', 201)
    })))
  })

  it('leaves the expiry alone when the request is rejected, and lets the owner ask again', function () {
    cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
      expiryOf(owner, course.id).then((before) => {
        ask(course).then(({ body: request }) => {
          cy.api('POST', `/api/course-extensions/${request.id}/reject`, { note: 'ยังไม่จำเป็น' }).then(({ status, body }) => {
            expect(status).to.equal(200)
            expect(body.status).to.equal('Rejected')
            expect(body.decisionNote).to.equal('ยังไม่จำเป็น')
          })
          cy.api('POST', `/api/course-extensions/${request.id}/approve`, {}).its('status').should('equal', 409)
        })
        expiryOf(owner, course.id).should('equal', before)
        ask(course).its('status').should('equal', 201)
      })
    }))
  })

  it('lets a therapist ask but not approve', function () {
    cy.login('therapist', 'therapist123').its('body.token').then((therapist) => {
      cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
        cy.apiAs(therapist, 'POST', '/api/course-extensions', { courseId: course.id, value: 1, unit: 'Month' })
          .then(({ status, body }) => {
            expect(status).to.equal(201)
            cy.apiAs(therapist, 'POST', `/api/course-extensions/${body.id}/approve`, {}).its('status').should('equal', 403)
            cy.apiAs(therapist, 'GET', '/api/course-extensions?status=Pending').its('status').should('equal', 403)
          })
      }))
    })
  })

  it('lists every pending request for a Manager', function () {
    cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
      ask(course).its('status').should('equal', 201)
      cy.api('GET', '/api/course-extensions?status=Pending').its('body').should((rows) => {
        expect(rows.map((row) => row.courseId)).to.include(course.id)
      })
    }))
  })

  it('rejects a missing length, an unknown unit and a cancelled course', function () {
    cy.registerPatient().then((owner) => buy(this.fixtures, owner).then((course) => {
      ask(course, { value: 0 }).its('status').should('equal', 400)
      ask(course, { unit: 'Week' }).its('status').should('equal', 400)
      cy.api('POST', `/api/course-purchases/${course.id}/cancel`, { reason: 'ทดสอบยกเลิก' }).its('status').should('equal', 200)
      ask(course).its('status').should('equal', 400)
    }))
  })
})

describe('v2 the sale list says what each line sold', () => {
  it('gives the type and name of a course sale and of a single-visit sale', function () {
    cy.registerPatient().then((patient) => {
      buy(this.fixtures, patient)

      cy.createServiceAppointment(this.fixtures, patient.id).then((appointment) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          salespersonStaffId: this.fixtures.managerStaffId,
          treatingStaffId: this.fixtures.therapistId,
          appointmentId: appointment.id,
          paymentLabelCode: 'FULL',
          items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
          payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
        }).its('status').should('equal', 201)
      })
    })

    cy.api('GET', '/api/payments').its('body.items').should((items) => {
      const types = items.map((item) => item.itemType).sort()
      expect(types).to.deep.equal(['CoursePurchase', 'SingleVisit'])
      expect(items.every(
          (item) => item.itemDescription && item.itemDescription.length > 0
      )).to.equal(true)
    })
  })
})

describe('v2 transactions of another branch are read-only for a therapist', () => {
  it('allows reading another branch but requires branch access and cancellation permission to cancel', function () {
    cy.login('therapist', 'therapist123').its('body.token').then((therapist01) => {
      cy.registerPatient({ branchCode: '02' }).then((patient) => {
        const sellAt02 = () =>
            cy.createServiceAppointment(this.fixtures, patient.id, {
              branchCode: '02',
              therapistId: this.fixtures.therapist2Id,
            }).then((appointment) =>
                cy.api('POST', '/api/payments', {
                  patientId: patient.id,
                  branchCode: '02',
                  salespersonStaffId: this.fixtures.therapist2Id,
                  treatingStaffId: this.fixtures.therapist2Id,
                  appointmentId: appointment.id,
                  paymentLabelCode: 'FULL',
                  items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
                  payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
                })
            )
        sellAt02().then(({ status, body: sale }) => {
          expect(status).to.equal(201)
          cy.apiAs(therapist01, 'GET', `/api/receipts/${sale.id}`).its('status').should('equal', 200)
          cy.apiAs(therapist01, 'POST', `/api/payments/${sale.id}/reverse`, { reason: 'ลองยกเลิกต่างสาขา' })
            .its('status').should('equal', 403)
          cy.api('GET', `/api/receipts/${sale.id}`).its('body.status').should('equal', 'PAID')
          cy.api('POST', `/api/payments/${sale.id}/reverse`, { reason: 'Manager ยกเลิก' }).its('status').should('equal', 200)
        })
      })
      cy.registerPatient().then((patient) => buy(this.fixtures, patient).then((course) => {
        cy.apiAs(therapist01, 'POST', `/api/course-purchases/${course.id}/cancel`, {
          reason: 'ไม่มีสิทธิ์ยกเลิกแม้อยู่สาขาตัวเอง',
        }).its('status').should('equal', 403)

        cy.api('POST', `/api/course-purchases/${course.id}/cancel`, {
          reason: 'Manager ยกเลิก',
        }).its('status').should('equal', 200)
      }))
    })
  })
})
