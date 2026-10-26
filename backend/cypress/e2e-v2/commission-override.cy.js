const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)
const range = '?from=2020-01-01&to=2099-12-31'

function buyAndClose(fx, patient) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id, courseId: fx.courseId, branchCode: '01', paymentLabelCode: 'FULL',
  }).then(({ body: purchase }) => {
    cy.api('POST', `/api/commission/closings/${month}?branchCode=01`).its('status').should('equal', 200)
    return cy.wrap(purchase)
  })
}

describe('v2 commission rate override and credited staff sales', () => {
  it('rejects a manual override and preserves the locked rate', function () {
    cy.registerPatient().then(patient => {
      buyAndClose(this.fixtures, patient).then(purchase => {
        cy.api('GET', `/api/patients/${patient.id}/courses`)
            .then(({ body: beforeRows }) => {
              const before = beforeRows.find(row => row.id === purchase.id)
              expect(before).to.exist

              cy.api('POST',
                  `/api/commission/courses/${purchase.id}/override-rate`,
                  { rate: 0.08, reason: 'ข้อตกลงพิเศษ' }
              ).then(({ status, body }) => {
                expect(status).to.equal(400)
                expect(body.message)
                    .to.contain('ไม่อนุญาตให้กำหนดอัตราคอมมิชชันโดยตรง')
              })

              cy.api('GET', `/api/patients/${patient.id}/courses`)
                  .then(({ body: afterRows }) => {
                    const after = afterRows.find(row => row.id === purchase.id)
                    expect(after).to.exist
                    expect(after.lockedCommissionRate)
                        .to.equal(before.lockedCommissionRate)
                  })
            })
      })
    })
  })

  it('refuses an override before the course is locked, without a reason, or with a rate over 100%', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
      }).then(({ body: purchase }) => {
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 0.08, reason: 'x' })
          .its('status').should('equal', 400)
      })
    })
    cy.registerPatient({ firstName: 'สอง', phone: '0822222222', identityNumber: '1100000000102' }).then((patient) => {
      buyAndClose(this.fixtures, patient).then((purchase) => {
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 0.08, reason: '' })
          .its('status').should('equal', 400)
        cy.api('POST', `/api/commission/courses/${purchase.id}/override-rate`, { rate: 1.5, reason: 'x' })
          .its('status').should('equal', 400)
      })
    })
  })

  it('denies therapist access and rejects a manager manual override', function () {
    cy.registerPatient().then(patient => {
      buyAndClose(this.fixtures, patient).then(purchase => {
        cy.login('therapist', 'therapist123').then(({ body: auth }) => {
          cy.apiAs(auth.token, 'POST',
              `/api/commission/courses/${purchase.id}/override-rate`,
              { rate: 0.08, reason: 'ทดสอบสิทธิ์' }
          ).its('status').should('equal', 403)
        })

        const reason = 'ทดสอบคำขอแก้อัตราที่ไม่อนุญาต'

        cy.api('POST',
            `/api/commission/courses/${purchase.id}/override-rate`,
            { rate: 0.07, reason }
        ).its('status').should('equal', 400)

        cy.api('GET', '/api/audit-logs?size=100')
            .its('body.items').then(items => {
          const entry = items.find(item =>
              item.tableName === 'course'
              && item.recordId === String(purchase.id)
              && String(item.newValue || '').includes(reason)
          )
          expect(entry, 'no course change from rejected override')
              .not.to.exist
        })
      })
    })
  })

  it('credits a course sale to its seller (the Owner PT) in full in the staff sales report', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
        caseOwnerStaffId: this.fixtures.therapistId,
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/staff-sales${range}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        const amountOf = (needle) => Number(body.rows.find((row) => row.fullName.includes(needle)).amount)
        expect(amountOf('Therapist Demo')).to.equal(9000)
      })
    })
  })

  it('credits a single-visit sale to its salesperson in full', function () {
    cy.registerPatient().then((patient) => {
      cy.createServiceAppointment(this.fixtures, patient.id).then((appointment) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          salespersonStaffId: this.fixtures.therapistId,
          treatingStaffId: this.fixtures.therapistId,
          appointmentId: appointment.id,
          paymentLabelCode: 'FULL',
          items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
          payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
        }).its('status').should('equal', 201)

        cy.api('GET', `/api/reports/staff-sales${range}`).then(({ body }) => {
          expect(body.rows).to.have.length(1)
          expect(Number(body.rows[0].amount)).to.equal(800)
        })
      })
    })
  })
})
