// Reopen before payment, the monthly payment of each staff member (Open -> Closed -> Paid), Finalize with the payment and
// adjustments after payment (documents' tests 05 and 06), plus who may do what.
const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)
const shiftMonth = (delta) => {
  const date = new Date(`${month}-01T00:00:00Z`)
  date.setUTCMonth(date.getUTCMonth() + delta)
  return date.toISOString().slice(0, 7)
}
const nextMonth = shiftMonth(1)
const lastMonth = shiftMonth(-1)
const money = (value) => Math.round(Number(value) * 100) / 100

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
const use = (courseId, extra = {}) => cy.useCourseSession(courseId, extra)
const closeMonth = () =>
    cy.api(
        'POST',
        `/api/commission/closings/${month}?branchCode=01`
    ).its('status').should('equal', 200)

const closingOf = (staffId) =>
    cy.api(
        'GET',
        `/api/commission/closings?month=${month}&branchCode=01`
    ).then(({ body }) =>
        body.find((row) => row.employeeId === staffId)
    )
const usages = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/usages`).its('body')
const allocations = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/allocations`).its('body')
const totals = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/totals`).its('body')
const prepare = (m = month) => cy.api('POST', `/api/commission/payments/prepare?month=${m}&branchCode=01`).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(200)
  return body
})
const paymentOf = (staffId, m = month) => prepare(m).then((rows) => rows.find((row) => row.staffId === staffId))
const closePayments = (m = month) =>
    cy.api(
        'POST',
        `/api/commission/payments/close?month=${m}&branchCode=01`
    )

describe('v2 commission: reopen the month before payment (test 05)', () => {
  it('works the tier out again from the sales at that time and allocates a new version', function () {
    template('ACC13', 10000, 13).then((smallId) => template('BIG1', 60000, 1).then((bigId) => {
      cy.registerPatient().then((patient) => buy(patient, smallId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200)
        closeMonth()
        usages(course.id).then((rows) => expect(money(rows[0].owner_net_commission)).to.equal(38.46)) // 10,000 at 5% / 13
        closingOf(this.fixtures.therapistId).then((closing) => expect(closing.status).to.equal('CLOSED'))

        // Reopen before adding sales, then close again to recalculate the tier.
        cy.api('POST', `/api/commission/closings/0/reopen`, { reason: 'x' }).its('status').should('equal', 400)
        closingOf(this.fixtures.therapistId).then((closing) => {
          cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: ' ' }).its('status').should('equal', 400)
          cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: 'ขายคอร์สเพิ่มก่อนจ่ายเงิน' }).then(({ status, body }) => {
            expect(status, JSON.stringify(body)).to.equal(200)
            expect(body.status).to.equal('OPEN')
          })
        })
        totals(course.id).then((row) => {
          expect(row.commission_status).to.equal('Provisional')
          expect(row.total_course_commission_pool).to.equal(null)
        })
        usages(course.id).then((rows) => expect(rows[0].owner_net_commission).to.equal(null))
        allocations(course.id).then((rows) => expect(rows.map((row) => row.entry_type)).to.deep.equal(['Allocation', 'Reversal']))
        buy(patient, bigId, this.fixtures.therapistId)

        closeMonth()
        totals(course.id).then((row) => {
          expect(row.commission_status).to.equal('Locked')
          expect(money(row.total_course_commission_pool)).to.equal(700)
          expect(money(row.commission_allocation_per_visit)).to.equal(53.85)
        })
        usages(course.id).then((rows) => expect(money(rows[0].owner_net_commission)).to.equal(53.85))
        allocations(course.id).then((rows) => {
          expect(rows.map((row) => `${row.entry_type}${row.allocation_version}`)).to.deep.equal(['Allocation1', 'Reversal1', 'Allocation2'])
          expect(money(rows[1].owner_net_commission)).to.equal(-38.46)
          expect(money(rows[2].owner_net_commission)).to.equal(53.85)
        })
        totals(course.id).then((row) => expect(money(row.owner_net_commission_released_total)).to.equal(53.85))
        // one Open row per staff and month: the second close used the same row
        cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`).its('body').should('have.length', 1)
      }))
    }))
  })

  it('refuses to reopen a month that is not closed, and a Finalized month', function () {
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(patient, courseId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200)
        closeMonth()
        closingOf(this.fixtures.therapistId).then((closing) => {
          cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: 'ครั้งแรก' }).its('status').should('equal', 200)
          cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: 'ซ้ำ' }).its('status').should('equal', 409)
        })
      }))
    })
  })

  it('is manager-only', function () {
    cy.login('therapist', 'therapist123').then(({ body: auth }) => {
      cy.apiAs(auth.token, 'POST', '/api/commission/closings/1/reopen', { reason: 'x' }).its('status').should('equal', 403)
      cy.apiAs(auth.token, 'GET', `/api/commission/payments?month=${month}&branchCode=01`).its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', `/api/commission/payments/prepare?month=${month}&branchCode=01`).its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', '/api/commission/payments/1/pay').its('status').should('equal', 403)
      cy.apiAs(auth.token, 'GET', '/api/commission/adjustments').its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', '/api/commission/adjustments', {}).its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', '/api/commission/adjustments/1/approve').its('status').should('equal', 403)
      cy.apiAs(auth.token, 'GET', `/api/commission/my-payments?month=${month}`).its('status').should('equal', 200)
    })
  })
})

describe('v2 commission: the monthly payment of a staff member', () => {
  const sellAndUse = (fx, sessions = 2) => template('ACC13', 10000, 13).then((courseId) => cy.registerPatient().then((patient) =>
    buy(patient, courseId, fx.therapistId).then((course) => {
      for (let i = 0; i < sessions; i += 1) use(course.id).its('status').should('equal', 200)
      return cy.wrap(course)
    })))

  it('stays unknown (not 0) until the sale month is closed, then adds the three kinds of money', function () {
    cy.api('POST', '/__cypress/treatment-fee-fixture', {
      feeType: 'Fixed',
      feeValue: 45,
    }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.updated).to.equal(1)
    })
    sellAndUse(this.fixtures).then((course) => {
      paymentOf(this.fixtures.therapistId).then((payment) => {
        expect(payment.status).to.equal('OPEN')
        expect(payment.pendingSessions).to.equal(2)
        expect(payment.courseCommission, 'unknown, not 0').to.equal(null)
        expect(payment.net).to.equal(null)
        closePayments().its('status').should('equal', 409)
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 409)
      })
      use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
      cy.createServiceAppointment(this.fixtures, course.patient.id)
          .then((appointment) => {
            return cy.api('POST', '/api/payments', {
              patientId: course.patient.id,
              branchCode: '01',
              appointmentId: appointment.id,
              salespersonStaffId: this.fixtures.therapistId,
              treatingStaffId: this.fixtures.therapistId,
              paymentLabelCode: 'FULL',
              items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
              payments: [{paymentMethodCode: 'BankTransfer', amount: 800,}],
            })
          }).its('status').should('equal', 201)
      closeMonth()
      paymentOf(this.fixtures.therapistId).then((payment) => {
        expect(payment.pendingSessions).to.equal(0)
        expect(money(payment.courseCommission)).to.equal(76.92) // 2 sessions x 38.46
        expect(money(payment.singleVisitFee)).to.equal(45)
        expect(money(payment.substituteFee)).to.equal(0)
        expect(money(payment.net)).to.equal(121.92)
      })
      paymentOf(this.fixtures.managerStaffId).then((payment) => {
        expect(money(payment.substituteFee)).to.equal(45)
        expect(money(payment.courseCommission)).to.equal(0)
        expect(money(payment.net)).to.equal(45)
      })
      // preparing again changes nothing: one payment per staff and month
      prepare().then((rows) => expect(rows.map((row) => row.staffId).sort()).to.deep.equal(
        [this.fixtures.managerStaffId, this.fixtures.therapistId].sort()))
    })
  })

  it('closes, reopens, and pays: the sale month is finalized with the payment and everything is locked', function () {
    sellAndUse(this.fixtures).then((course) => {
      closeMonth()
      paymentOf(this.fixtures.therapistId).then((payment) => {
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 409) // must be Closed first
        closePayments().then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)

          const closed = body.find((row) => row.id === payment.id)
          expect(closed).to.exist
          expect(closed.status).to.equal('CLOSED')
          expect(money(closed.net)).to.equal(76.92)
          expect(money(closed.adjustment)).to.equal(0)
        })
        cy.api('POST', `/api/commission/payments/${payment.id}/reopen`).its('body.status').should('equal', 'OPEN')
        closePayments().its('status').should('equal', 200)
        closingOf(this.fixtures.therapistId).then((closing) => expect(closing.status).to.equal('CLOSED'))
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)
          expect(body.status).to.equal('PAID')
          expect(body.paidAt).to.be.a('string')
          expect(money(body.net)).to.equal(76.92)
        })
        // the rate behind the money paid can no longer be reopened
        closingOf(this.fixtures.therapistId).then((closing) => {
          expect(closing.status).to.equal('FINALIZED')
          cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: 'ลองย้อน' }).its('status').should('equal', 409)
        })
        cy.api('POST', `/api/commission/payments/${payment.id}/reopen`).its('status').should('equal', 409)
        closePayments().then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)

          const paid = body.find((row) => row.id === payment.id)
          expect(paid).to.exist
          expect(paid.status).to.equal('PAID')
          expect(money(paid.net)).to.equal(76.92)
        })
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 409)
        // a session whose money was paid cannot be reversed either
        cy.api('GET', `/api/course-purchases/${course.id}`).then(({ body }) => {
          const movement = body.movements.find((entry) => entry.type === 'USAGE')
          cy.api('POST', `/api/course-movements/${movement.id}/reverse`, { reason: 'ย้อนหลังจ่าย' }).its('status').should('equal', 409)
        })
        allocations(course.id).should('have.length', 2)
      })
    })
  })

  it('a PT reads their own payments, nobody else\'s', function () {
    sellAndUse(this.fixtures).then(() => {
      closeMonth()
      prepare()
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        cy.apiAs(auth.token, 'GET', `/api/commission/my-payments?month=${month}`).then(({ status, body }) => {
          expect(status).to.equal(200)
          body.forEach((row) => expect(row.staffId).to.equal(this.fixtures.therapistId))
        })
      })
    })
  })
})
