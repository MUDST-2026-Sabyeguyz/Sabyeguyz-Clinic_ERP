// T3: the commission tables are mapped by entities that save and read back, and the database refuses rows that break
// the Data Dictionary rules (checked through a test-only endpoint; the prerequisites are made through the public API).
const check = (path, ids) => cy.api('POST', `/__cypress/commission-entities/${path}`, ids).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(200)
  return body
})

describe('v2 commission tables (entities and database rules)', () => {
  beforeEach(function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01', salespersonStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
      }).then(({ body: sale }) => {
        cy.api('GET', `/api/receipts/${sale.id}`).then(({ body: receipt }) => {
          cy.api('POST', `/api/payments/${receipt.payments[0].id}/refunds`, {
            amount: 300, paymentMethodCode: 'BankTransfer', reason: 'ลูกค้าไม่พอใจ',
          }).then(({ body: refund }) => {
            cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',})
              .then(({ body: course }) => {
                cy.api('GET', '/__cypress/commission-entities/latest').then(({ body: latest }) => {
                  cy.login('manager', 'manager123').then(({ body: auth }) => {
                    cy.wrap({
                      refundId: refund.id, courseId: course.id, visitId: latest.visitId, ruleId: latest.ruleId,
                      staffId: this.fixtures.therapistId, userId: auth.id,
                    }).as('ids')
                  })
                })
              })
          })
        })
      })
    })
  })

  it('saves a course refund item and refuses a second one for the same course or a zero amount', function () {
    check('refund-item', this.ids).then((body) => {
      expect(body.id).to.be.a('number')
      expect(body.courseId).to.equal(this.ids.courseId)
      expect(body.refundId).to.equal(this.ids.refundId)
      expect(Number(body.amount)).to.equal(300)
      expect(body.cancelledVisits).to.equal(2)
      expect(body.duplicate.rejected, 'one item per refund and course').to.contain('uq_course_refund_item_refund_course')
      expect(body.zeroAmount.rejected).to.contain('chk_course_refund_item_amount')
    })
  })

  it('saves a single-visit fee, its reversal and keeps the one-reversal and reason rules', function () {
    check('single-visit-fee', this.ids).then((body) => {
      expect(body.allocation).to.include({ entryType: 'Allocation', feeType: 'Percentage', version: 11 })
      expect(Number(body.allocation.feeAmount)).to.equal(100)
      expect(body.reversalWithoutReason.rejected).to.contain('chk_single_visit_fee_entry')
      expect(body.reversal.reversesAllocation).to.equal(true)
      expect(body.secondReversalOfTheSameFee.rejected).to.contain('uq_single_visit_fee_reverses')
      expect(body.negativeAllocation.rejected).to.contain('chk_single_visit_fee_entry')
    })
  })

  it('saves monthly payments and enforces Paid needing the amounts, their sum and the paid date', function () {
    check('payments', this.ids).then((body) => {
      expect(body.open).to.include({ month: '2026-10', status: 'Open' })
      expect(body.paid).to.include({ status: 'Paid' })
      expect(Number(body.paid.net)).to.equal(2860)
      expect(body.paidWithoutAmounts.rejected).to.contain('chk_staff_monthly_payment_amounts')
      expect(body.paidWithWrongNet.rejected).to.contain('chk_staff_monthly_payment_amounts')
      expect(body.paidWithoutPaidAt.rejected).to.contain('chk_staff_monthly_payment_paid_at')
      expect(body.secondRowForTheSameStaffAndMonth.rejected, 'one payout per staff and month').to.contain('duplicate key')
    })
  })

  it('saves a pay adjustment and its applications and enforces the adjustment rules', function () {
    check('adjustments', this.ids).then((body) => {
      expect(body.minusFromSeptember).to.include({ status: 'Pending', originalMonth: '2026-09', appliedMonth: '2026-10' })
      expect(Number(body.minusFromSeptember.amount)).to.equal(-140)
      expect(Number(body.minusFromSeptember.remaining)).to.equal(140)
      expect(body.appliedMonthNotAfterOriginal.rejected).to.contain('chk_staff_pay_adjustment_months')
      expect(body.zeroAmount.rejected).to.contain('chk_staff_pay_adjustment_amount')
      expect(body.noSource.rejected).to.contain('chk_staff_pay_adjustment_source')
      expect(body.blankReason.rejected).to.contain('chk_staff_pay_adjustment_reason')
      expect(body.approvedStatusWithoutApprover.rejected).to.contain('chk_staff_pay_adjustment_status_approval')
      expect(body.remainingNotMatchingBalance.rejected).to.contain('chk_staff_pay_adjustment_balance')
      expect(body.draftApplication).to.include({ status: 'Draft' })
      expect(body.appliedWithoutApprover.rejected).to.contain('chk_adjustment_application_applied_approval')
      expect(body.appliedDeductionWithoutReference.rejected).to.contain('chk_adjustment_application_deduction_reference')
      expect(body.appliedDeductionWithReference).to.include({ status: 'Applied' })
    })
  })
})
