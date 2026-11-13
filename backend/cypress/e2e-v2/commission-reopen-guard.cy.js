/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
// Reopen is closed for the whole branch month as soon as one person is Paid; the Manager can reopen the whole branch
// with one reason while nobody is paid; the closing history shows the reopen and its reason.
const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)

const template = (code, price, sessions) => cy.api('POST', '/api/courses',
  { code, name: `คอร์ส ${code}`, price, sessions, bonusSessions: 0, validityDays: 365 }).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(201)
  return body.id
})
const buy = (patient, courseId, ownerId) => cy.api('POST', '/api/course-purchases', {
  patientId: patient.id, courseId, branchCode: '01', caseOwnerStaffId: ownerId, paymentLabelCode: 'FULL',
}).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(201)
  return body
})
const closeMonth = () => cy.api('POST', `/api/commission/closings/${month}?branchCode=01`).its('status').should('equal', 200)
const closingOf = (staffId) => cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`)
  .then(({ body }) => body.find((row) => row.employeeId === staffId))
const closePayments = () => cy.api('POST', `/api/commission/payments/close?month=${month}&branchCode=01`).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(200)
  return body
})
const reopenAll = (reason) => cy.api({
  method: 'POST',
  url: `/api/commission/branch-closings/${month}/reopen-all?branchCode=01`,
  body: { reason },
  failOnStatusCode: false,
})

describe('v2 commission: reopening a branch month', () => {
  // the owner uses one session and the Manager treats another as a substitute, so two people have a payment
  const closedRound = (fx) => {
    cy.api('POST', '/__cypress/treatment-fee-fixture', { feeType: 'Fixed', feeValue: 45 }).its('status').should('equal', 200)
    return template('ACC13', 10000, 13).then((courseId) => cy.registerPatient().then((patient) =>
      buy(patient, courseId, fx.therapistId).then((course) => {
        cy.useCourseSession(course.id).its('status').should('equal', 200)
        cy.useCourseSession(course.id, { treatingStaffId: fx.managerStaffId }).its('status').should('equal', 200)
        closeMonth()
        return closePayments()
      })))
  }

  it('refuses every kind of Reopen once one person of the branch is Paid', function () {
    closedRound(this.fixtures).then((payments) => {
      const owner = payments.find((row) => row.staffId === this.fixtures.therapistId)
      const substitute = payments.find((row) => row.staffId === this.fixtures.managerStaffId)
      expect(owner.status).to.equal('CLOSED')
      expect(substitute.status).to.equal('CLOSED')

      cy.api('POST', `/api/commission/payments/${substitute.id}/pay`).its('body.status').should('equal', 'PAID')

      // the owner is still Closed and unpaid, yet nothing of the month can be reopened any more
      cy.api({ method: 'POST', url: `/api/commission/payments/${owner.id}/reopen`, body: { reason: 'ลองเปิดหลังมีคนจ่าย' }, failOnStatusCode: false })
        .its('status').should('equal', 409)
      closingOf(this.fixtures.therapistId).then((closing) => {
        cy.api({ method: 'POST', url: `/api/commission/closings/${closing.id}/reopen`, body: { reason: 'ลองเปิดหลังมีคนจ่าย' }, failOnStatusCode: false })
          .its('status').should('equal', 409)
      })
      reopenAll('ลองเปิดทั้งสาขาหลังมีคนจ่าย').its('status').should('equal', 409)
    })
  })

  it('reopens the whole branch with one reason and can be closed again', function () {
    closedRound(this.fixtures).then(() => {
      reopenAll('  ').its('status').should('equal', 400)

      reopenAll('ยอดขายคอร์สผิด').then(({ status, body }) => {
        expect(status, JSON.stringify(body)).to.equal(200)
        expect(body.status).to.equal('OPEN')
      })
      cy.api('GET', `/api/commission/payments?month=${month}&branchCode=01`).then(({ body }) => {
        expect(body.length).to.be.greaterThan(0)
        body.forEach((row) => expect(row.status).to.equal('OPEN'))
      })
      closingOf(this.fixtures.therapistId).then((closing) => {
        expect(closing.status).to.equal('OPEN')
      })

      // closing again works and nobody is left behind
      closeMonth()
      closePayments().then((rows) => rows.forEach((row) => expect(row.status).to.equal('CLOSED')))
    })
  })

  it('shows the reopen, its reason and the close in the history of the month', function () {
    closedRound(this.fixtures).then(() => {
      reopenAll('ยอดขายคอร์สผิด').its('status').should('equal', 200)
      closeMonth()
      cy.api('GET', '/api/commission/closing-history?branchCode=01').then(({ status, body }) => {
        expect(status, JSON.stringify(body)).to.equal(200)
        const entry = body.find((row) => row.month === month)
        expect(entry, 'history of the month').to.exist
        expect(entry.reopenCount).to.equal(1)
        const reopen = entry.events.find((event) => event.kind === 'reopen')
        expect(reopen, 'reopen event').to.exist
        expect(reopen.reason).to.equal('ยอดขายคอร์สผิด')
        expect(entry.events.some((event) => event.kind === 'close')).to.equal(true)
      })
    })
  })
})
