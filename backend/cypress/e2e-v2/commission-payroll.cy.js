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
  patientId: patient.id, courseId, branchCode: '01', caseOwnerStaffId: ownerId,
}).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(201)
  return body
})
const use = (courseId, extra = {}) => cy.api('POST', '/api/course-usages', { coursePurchaseId: courseId, ...extra })
const closeMonth = () => cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
const closingOf = (staffId) => cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body }) => body.find((row) => row.employeeId === staffId))
const usages = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/usages`).its('body')
const allocations = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/allocations`).its('body')
const totals = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/totals`).its('body')
const prepare = (m = month) => cy.api('POST', `/api/commission/payments/prepare?month=${m}`).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(200)
  return body
})
const paymentOf = (staffId, m = month) => prepare(m).then((rows) => rows.find((row) => row.staffId === staffId))

describe('v2 commission: reopen the month before payment (test 05)', () => {
  it('works the tier out again from the sales at that time and allocates a new version', function () {
    template('ACC13', 10000, 13).then((smallId) => template('BIG1', 60000, 1).then((bigId) => {
      cy.registerPatient().then((patient) => buy(patient, smallId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200)
        closeMonth()
        usages(course.id).then((rows) => expect(money(rows[0].owner_net_commission)).to.equal(38.46)) // 10,000 at 5% / 13
        closingOf(this.fixtures.therapistId).then((closing) => expect(closing.status).to.equal('CLOSED'))

        // more is sold in the same month: the tier changes (70,000 = 7%), but a Closed month keeps its rate until reopened
        buy(patient, bigId, this.fixtures.therapistId)
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
        cy.api('GET', `/api/commission/closings?month=${month}`).its('body').should('have.length', 1)
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
      cy.apiAs(auth.token, 'GET', `/api/commission/payments?month=${month}`).its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', `/api/commission/payments/prepare?month=${month}`).its('status').should('equal', 403)
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
    sellAndUse(this.fixtures).then((course) => {
      paymentOf(this.fixtures.therapistId).then((payment) => {
        expect(payment.status).to.equal('OPEN')
        expect(payment.pendingSessions).to.equal(2)
        expect(payment.courseCommission, 'unknown, not 0').to.equal(null)
        expect(payment.net).to.equal(null)
        cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('status').should('equal', 409)
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 409)
      })
      // a substitute session and a single visit by the same PT add the other two kinds
      cy.api('GET', '/api/commission/treatment-fees').then(({ body }) => cy.api('PUT', `/api/commission/treatment-fees/${body[0].id}`,
        { feeType: 'Fixed', feeValue: 45 }).its('status').should('equal', 200))
      use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
      cy.api('POST', '/api/payments', {
        patientId: course.patient.id, branchCode: '01', salespersonStaffId: this.fixtures.therapistId, treatingStaffId: this.fixtures.therapistId,
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
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
        cy.api('POST', `/api/commission/payments/${payment.id}/close`).then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)
          expect(body.status).to.equal('CLOSED')
          expect(money(body.net)).to.equal(76.92)
          expect(money(body.adjustment)).to.equal(0)
        })
        cy.api('POST', `/api/commission/payments/${payment.id}/reopen`).its('body.status').should('equal', 'OPEN')
        cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('status').should('equal', 200)
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
        cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('status').should('equal', 409)
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

describe('v2 commission: adjustments after payment (test 06)', () => {
  const paidMonthOf = (fx, sessions = 2) => template('ACC13', 10000, 13).then((courseId) => cy.registerPatient().then((patient) =>
    buy(patient, courseId, fx.therapistId).then((course) => {
      for (let i = 0; i < sessions; i += 1) use(course.id).its('status').should('equal', 200)
      closeMonth()
      return paymentOf(fx.therapistId).then((payment) => {
        cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('status').should('equal', 200)
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 200)
        return allocations(course.id).then((rows) => ({ course, payment, allocation: rows[0] }))
      })
    })))
  const adjust = (fx, ctx, amount, extra = {}) => cy.api('POST', '/api/commission/adjustments', {
    staffId: fx.therapistId, amount, reason: 'แก้ไขข้อมูลหลังจ่าย', allocationId: ctx.allocation.allocation_id, ...extra,
  })

  it('shows -140 in the next period and leaves the paid period as it was', function () {
    paidMonthOf(this.fixtures).then((ctx) => {
      adjust(this.fixtures, ctx, -140).then(({ status, body }) => {
        expect(status, JSON.stringify(body)).to.equal(201)
        expect(body.status).to.equal('Pending')
        expect(body.originalMonth).to.equal(month)
        expect(body.appliedMonth).to.equal(nextMonth)
        expect(money(body.remaining)).to.equal(140)
        cy.api('POST', `/api/commission/adjustments/${body.id}/approve`).its('body.status').should('equal', 'Approved')
        cy.api('POST', `/api/commission/adjustments/${body.id}/applications`, { month: nextMonth }).then(({ status: s2, body: app }) => {
          expect(s2, JSON.stringify(app)).to.equal(201)
          expect(money(app.amount)).to.equal(-140)
          expect(app.status).to.equal('Draft')
        })
        // not in the paid month
        cy.api('POST', `/api/commission/adjustments/${body.id}/applications`, { month }).its('status').should('equal', 400)
      })
      // the paid period is unchanged; the next period carries the draft
      cy.api('GET', `/api/commission/payments/${ctx.payment.id}`).then(({ body }) => {
        expect(body.status).to.equal('PAID')
        expect(money(body.net)).to.equal(76.92)
        expect(money(body.adjustment)).to.equal(0)
      })
      cy.api('GET', `/api/commission/payments?month=${nextMonth}`).then(({ body }) => {
        const next = body.find((row) => row.staffId === this.fixtures.therapistId)
        expect(next.status).to.equal('OPEN')
        expect(next.applications.map((app) => money(app.amount))).to.deep.equal([-140])
      })
    })
  })

  it('needs a source of the same staff member, a reason and a non-zero amount', function () {
    paidMonthOf(this.fixtures).then((ctx) => {
      adjust(this.fixtures, ctx, 0).its('status').should('equal', 400)
      adjust(this.fixtures, ctx, 10, { reason: ' ' }).its('status').should('equal', 400)
      cy.api('POST', '/api/commission/adjustments', { staffId: this.fixtures.therapistId, amount: 10, reason: 'ไม่มีต้นทาง' })
        .its('status').should('equal', 400)
      adjust({ therapistId: this.fixtures.managerStaffId }, ctx, 10).its('status').should('equal', 400) // someone else's allocation
      adjust(this.fixtures, ctx, 10, { originalMonth: nextMonth, appliedMonth: nextMonth }).its('status').should('equal', 400)
    })
  })

  it('pays an addition with the next payment and marks the adjustment Applied', function () {
    paidMonthOf(this.fixtures).then((ctx) => {
      adjust(this.fixtures, ctx, 50).then(({ body }) => {
        cy.api('POST', `/api/commission/adjustments/${body.id}/approve`).its('status').should('equal', 200)
        cy.api('POST', `/api/commission/adjustments/${body.id}/applications`, { month: nextMonth }).then(({ body: app }) => {
          // a draft that the Manager has not approved is not paid
          paymentOf(this.fixtures.therapistId, nextMonth).then((payment) => expect(money(payment.adjustment)).to.equal(0))
          cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, {}).its('status').should('equal', 200)
        })
        paymentOf(this.fixtures.therapistId, nextMonth).then((payment) => {
          expect(money(payment.adjustment)).to.equal(50)
          cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('body.net').then((net) => expect(money(net)).to.equal(50))
          cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('body.status').should('equal', 'PAID')
        })
        cy.api('GET', `/api/commission/adjustments/${body.id}`).then(({ body: done }) => {
          expect(done.status).to.equal('Applied')
          expect(money(done.processed)).to.equal(50)
          expect(money(done.remaining)).to.equal(0)
          expect(done.applications[0].status).to.equal('Applied')
          // an applied application cannot be cancelled and the adjustment cannot be cancelled
          cy.api('POST', `/api/commission/adjustment-applications/${done.applications[0].id}/cancel`).its('status').should('equal', 400)
          cy.api('POST', `/api/commission/adjustments/${done.id}/cancel`).its('status').should('equal', 400)
        })
      })
    })
  })

  it('never deducts more than was earned in the month: the rest is carried and a resigned PT goes to external review', function () {
    template('ACC13', 10000, 13).then((courseId) => cy.registerPatient().then((patient) => buy(patient, courseId, this.fixtures.therapistId).then((course) => {
      use(course.id).its('status').should('equal', 200)
      closeMonth()
      allocations(course.id).then((rows) => {
        // earlier month's mistake, deducted from this month's earnings of 38.46
        cy.api('POST', '/api/commission/adjustments', {
          staffId: this.fixtures.therapistId, originalMonth: lastMonth, appliedMonth: month, amount: -500,
          reason: 'จ่ายเกินเดือนก่อน', allocationId: rows[0].allocation_id,
        }).then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(201)
          cy.api('POST', `/api/commission/adjustments/${body.id}/approve`).its('status').should('equal', 200)
          cy.api('POST', `/api/commission/adjustments/${body.id}/applications`, { month }).then(({ body: app }) => {
            // a deduction needs the consent reference
            cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, {}).its('status').should('equal', 400)
            cy.api('POST', `/api/commission/adjustment-applications/${app.id}/approve`, { reference: 'ใบยินยอม 2569/10' }).its('status').should('equal', 200)
          })
          paymentOf(this.fixtures.therapistId).then((payment) => {
            expect(money(payment.adjustment)).to.equal(-38.46)
            expect(money(payment.net)).to.equal(0)
            cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('body.net').then((net) => expect(money(net)).to.equal(0))
            cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 200)
          })
          cy.api('GET', `/api/commission/adjustments/${body.id}`).then(({ body: partly }) => {
            expect(partly.status).to.equal('PartiallyApplied')
            expect(money(partly.processed)).to.equal(38.46)
            expect(money(partly.remaining)).to.equal(461.54)
            expect(money(partly.applications[0].amount)).to.equal(-38.46)
          })
          // not resigned yet: no external review; after the resignation the balance is handled outside the system
          cy.api('POST', `/api/commission/adjustments/${body.id}/external-review`).its('status').should('equal', 400)
          cy.api('POST', `/api/staff/${this.fixtures.therapistId}/terminate`, { terminationDate: month + '-28' }).its('status').should('equal', 200)
          cy.api('POST', `/api/commission/adjustments/${body.id}/external-review`).its('body.status').should('equal', 'ExternalReview')
        })
      })
    })))
  })
})
