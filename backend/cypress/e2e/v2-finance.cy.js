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
    'payment-void': 'rejectsPaymentAgainstAVoidedTransaction',
    'payment-zero': 'rejectsZeroOrNegativeAmount',
    'receipt-create': 'createsAReceipt',
    'receipt-duplicate': 'rejectsDuplicateReceiptNo',
    'sale-assessment': 'recordsAnAssessmentSale',
    'sale-wrong-type': 'rejectsCoursePurchaseInRecordVisitSale',
    'sale-course': 'coursePurchaseCreatesTheCourseFirstThenTheTransaction',
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
    ['receipt-create', { number: 'RE-2600001', date: '2026-08-18' }],
    ['sale-assessment', { amount: 1200, status: true }],
    ['sale-course', { sameCourse: true, amount: 10000, type: 'CoursePurchase', saveOrder: ['course', 'transaction'] }],
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
