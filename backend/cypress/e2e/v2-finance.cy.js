// One Cypress case for each original commission/sale JUnit test (24 total).
describe('Clinic v2 commission and sale services', () => {
  const originalNames = {
    'override-applies': 'recordsTheAuditAndAppliesTheNewRate',
    'override-reason': 'requiresAReason',
    'override-unlocked': 'rejectsOverridingACourseThatWasNeverLocked',
    'scheme-covers': 'picksTheSchemeWhoseRangeCoversTheDate',
    'scheme-newest': 'newestVersionWinsWhenRangesOverlapAtTheBoundary',
    'scheme-missing': 'throwsWhenNoSchemeCoversTheDate',
    'tier-example': 'resolvesTheSeventyPercentTierFromTheRequirementDocExample',
    'tier-top': 'openEndedTopTierHasNoUpperBound',
    'tier-missing': 'throwsWhenNoTierCoversTheAmount',
    'closing-locks': 'locksTheRateAndStampsItOntoEveryCourseSoldThatMonth',
    'closing-duplicate': 'rejectsClosingTheSameStaffAndMonthTwice',
    'closing-malformed': 'rejectsAMalformedMonth',
    'fee-fixed': 'fixedFeeMatchesTheDocExample',
    'fee-percentage': 'percentageFeeMatchesTheDocExample',
    'fee-specific': 'prefersAStaffSpecificRuleOverADefaultRule',
    'payment-record': 'recordsAPaymentAgainstAnActiveTransaction',
    'payment-void': 'rejectsPaymentAgainstAVoidedReceipt',
    'payment-zero': 'rejectsZeroOrNegativeAmount',
    'receipt-create': 'createsAReceipt',
    'receipt-duplicate': 'rejectsDuplicateReceiptNo',
    'cancel-void-paid-sale': 'voidingTheOnlyLineVoidsItsPaymentsAndTheReceipt',
    'cancel-void-one-of-two-lines': 'voidingOneOfTwoLinesLeavesPaymentsAndReceiptAlone',
    'cancel-void-payment': 'voidingAPaymentRecordsAVoidCancellationOnThePayment',
    'cancel-requires-reason': 'cancellationRequiresAReason',
    'cancel-requires-permission': 'cancellationRequiresAPermission',
    'cancel-already-void-sale': 'rejectsVoidingAnAlreadyVoidedSale',
    'refund-request': 'refundRequestStartsPendingUnderARefundCancellation',
    'refund-over': 'rejectsRefundsThatTogetherExceedThePayment',
    'refund-voided-payment': 'rejectsRefundingAVoidedPayment',
    'refund-complete': 'completingARefundStampsWhoAndWhen',
    'refund-complete-twice': 'rejectsCompletingARefundThatIsNotPending',
    'refund-fail': 'failingARefundLeavesNoRefundedAt',
    'revenue-single-line': 'revenueOfASingleLineReceiptGoesToThatType',
    'revenue-mixed': 'revenueSplitsAcrossLinesByWhatEachCostsAfterBillDiscount',
    'revenue-rounding': 'revenueSplitRemainderGoesToTheLastLine',
    'revenue-no-lines': 'revenueOfAReceiptWithoutActiveLinesCountsAsSingleVisit',
    'receipt-bad-format': 'rejectsAReceiptNumberThatIsNotREDashSevenDigits',
    'receipt-next-no': 'nextReceiptNumberIsTheHighestInUsePlusOne',
    'receipt-totals': 'billDiscountIsAllocatedToLinesWithoutChangingCommissionableAmount',
    'receipt-totals-rounding': 'lastLineTakesTheRoundingRemainderOfTheBillDiscount',
    'receipt-totals-no-lines': 'rejectsFinalizingAReceiptWithoutLines',
    'sale-single-visit': 'recordsASingleVisitSale',
    'sale-assessment-rejected': 'rejectsAnAssessmentSale',
    'sale-course-discount-percent': 'courseLinePercentDiscountBecomesTheCommissionablePrice',
    'sale-course-discount-amount': 'courseLineBahtDiscountBecomesTheCommissionablePrice',
    'sale-visit-discount': 'visitLineDiscountReducesTheAmountButKeepsTheUnitPrice',
    'discount-percent': 'percentDiscountMatchesTheFeedbackExample',
    'discount-amount': 'bahtDiscountMatchesTheFeedbackExample',
    'discount-none': 'noDiscountKeepsThePrice',
    'discount-percent-over': 'rejectsAPercentDiscountAboveOneHundred',
    'discount-amount-over': 'rejectsADiscountLargerThanThePrice',
    'discount-unknown-type': 'rejectsAnUnknownDiscountType',
    'discount-value-without-type': 'rejectsADiscountValueWithoutAType',
    'preview-discount': 'previewsCommissionOnThePriceAfterTheLineDiscount',
    'preview-running-total': 'previewUsesTheSellersRunningMonthlyTotalForTheTier',
    'preview-invalid-template': 'previewRejectsANonCourseService',
    'sale-wrong-type': 'rejectsCoursePurchaseInRecordVisitSale',
    'sale-course': 'coursePurchaseRecordsTheTransactionFirstThenTheCourse',
    'sale-void': 'voidRejectsAnAlreadyVoidedTransaction',
    'schemecrud-create': 'createsASchemeWithItsTiers',
    'schemecrud-next-version': 'nextVersionIncrementsFromTheLatestExistingScheme',
    'schemecrud-update-keeps-tiers': 'updateOnlyTouchesNameAndDatesNeverTiers',
  }
  const cases = [
    ['override-applies', { oldRate: 0.07, newRate: 0.10, rate: 0.10, pool: 1000, outstanding: 790 }],
    ['scheme-covers', { sameV1: true, sameV2: false }],
    ['scheme-newest', { sameV1: false, sameV2: true }],
    ['tier-example', 0.07],
    ['tier-top', 0.10],
    ['closing-locks', { rate: 0.07, status: true, courseStatus: 'Locked', courseRate: 0.07, pool: 700, perVisit: 70 }],
    ['fee-fixed', 45],
    ['fee-percentage', 30],
    ['fee-specific', { present: true, fee: 45 }],
    ['payment-record', { amount: 1200, status: true }],
    ['receipt-create', { number: 'RE-2600001', date: '2026-08-18T00:00:00' }],
    ['receipt-next-no', 'RE-0000042'],
    ['cancel-void-paid-sale', { saleStatus: false, paymentStatus: false, receiptStatus: 'Voided', cancellations: 2, types: ['Void', 'Void'], targets: ['payment', 'transaction'], cancelledBy: 9 }],
    ['cancel-void-one-of-two-lines', { saleStatus: false, paymentStatus: true, receiptStatus: 'Issued', cancellations: 1, types: ['Void'], targets: ['transaction'], cancelledBy: 9 }],
    ['cancel-void-payment', { paymentStatus: false, type: 'Void', targetsPayment: true, reason: 'ลูกค้าขอยกเลิก' }],
    ['refund-request', { status: 'Pending', amount: 400, requestedBy: 9, hasRefundedAt: false, cancellationType: 'Refund', sameCancellation: true }],
    ['refund-complete', { status: 'Completed', refundedBy: 9, hasRefundedAt: true }],
    ['refund-fail', { status: 'Failed', hasRefundedAt: false }],
    ['revenue-single-line', { CoursePurchase: 1500 }],
    ['revenue-mixed', { CoursePurchase: 1350, SingleVisit: 450 }],
    ['revenue-rounding', { SingleVisit: 66.66, CoursePurchase: 33.34 }],
    ['revenue-no-lines', { SingleVisit: 250 }],
    ['receipt-totals', { subtotal: 2000, billDiscount: 200, taxable: 1800, tax: 0, net: 1800, billType: 'Fixed', allocated: [150, 50], commissionable: [1500, 500] }],
    ['receipt-totals-rounding', { subtotal: 300, billDiscount: 100, taxable: 200, tax: 0, net: 200, billType: 'Fixed', allocated: [33.33, 33.33, 33.34], commissionable: [100, 100, 100] }],
    ['sale-single-visit', { amount: 1200, status: true }],
    ['sale-course-discount-percent', { unitPrice: 2000, amount: 1500, commissionable: 1500, coursePrice: 1500 }],
    ['sale-course-discount-amount', { unitPrice: 2000, amount: 1500, commissionable: 1500, coursePrice: 1500 }],
    ['sale-visit-discount', { unitPrice: 1200, amount: 1080, lineDiscount: 120, lineDiscountType: 'Percentage', commissionable: 1080, lineNo: 1 }],
    ['discount-percent', 1500],
    ['discount-amount', 1500],
    ['discount-none', 2000],
    ['preview-discount', { net: 1500, rate: 0.05, pool: 75, perVisit: 7.5, monthlySales: 1500 }],
    ['preview-running-total', { net: 1500, rate: 0.06, pool: 90, perVisit: 9, monthlySales: 61500 }],
    ['sale-course', { sameCourse: true, sameLine: true, amount: 10000, type: 'CoursePurchase', saveOrder: ['transaction', 'course'] }],
    ['schemecrud-create', { version: 1, tiersSaved: 2, firstRate: 0.05 }],
    ['schemecrud-next-version', { version: 2 }],
    ['schemecrud-update-keeps-tiers', { name: 'Renamed', tiersSaveCallsDuringUpdate: 0 }],
  ]
  cases.forEach(([scenario, expected]) => {
    it(originalNames[scenario], () => {
      cy.api('POST', `/__cypress/v2/finance/${scenario}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).not.to.have.property('error')
        expect(body.value).to.deep.equal(expected)
      })
    })
  })
  const failures = [
    ['override-reason', 'IllegalArgumentException'],
    ['override-unlocked', 'IllegalArgumentException'],
    ['scheme-missing', 'IllegalStateException'],
    ['tier-missing', 'IllegalStateException'],
    ['closing-duplicate', 'IllegalStateException'],
    ['closing-malformed', 'IllegalArgumentException'],
    ['payment-void', 'IllegalArgumentException'],
    ['payment-zero', 'IllegalArgumentException'],
    ['receipt-duplicate', 'IllegalArgumentException'],
    ['sale-wrong-type', 'IllegalArgumentException'],
    ['sale-void', 'IllegalArgumentException'],
    ['sale-assessment-rejected', 'IllegalArgumentException'],
    ['receipt-bad-format', 'IllegalArgumentException'],
    ['receipt-totals-no-lines', 'IllegalArgumentException'],
    ['cancel-requires-reason', 'IllegalArgumentException'],
    ['cancel-requires-permission', 'IllegalArgumentException'],
    ['cancel-already-void-sale', 'IllegalArgumentException'],
    ['refund-over', 'IllegalArgumentException'],
    ['refund-voided-payment', 'IllegalArgumentException'],
    ['refund-complete-twice', 'IllegalArgumentException'],
    ['discount-percent-over', 'IllegalArgumentException'],
    ['discount-amount-over', 'IllegalArgumentException'],
    ['discount-unknown-type', 'IllegalArgumentException'],
    ['discount-value-without-type', 'IllegalArgumentException'],
    ['preview-invalid-template', 'IllegalArgumentException'],
  ]
  failures.forEach(([scenario, error]) => {
    it(originalNames[scenario], () => {
      cy.api('POST', `/__cypress/v2/finance/${scenario}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.error).to.equal(error)
      })
    })
  })
  // schemecrud-overlap/-gap call V2RepositoryStub.outcome() themselves inside
  // schemeCrud(), so their IllegalArgumentException is caught one level down:
  // the outer run() wraps that already-caught result in its own {value: ...}.
  const nestedFailures = [
    ['schemecrud-overlap', 'rejectsOverlappingTierRanges', 'IllegalArgumentException'],
    ['schemecrud-gap', 'rejectsAGapBetweenTierRanges', 'IllegalArgumentException'],
  ]
  nestedFailures.forEach(([scenario, name, error]) => {
    it(name, () => {
      cy.api('POST', `/__cypress/v2/finance/${scenario}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).not.to.have.property('error')
        expect(body.value.error).to.equal(error)
      })
    })
  })
})
