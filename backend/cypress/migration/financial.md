# Financial JUnit → Cypress migration

All original assertions run in Cypress against real production HTTP endpoints. Java fixture helpers only seed exceptional fixtures, expire a purchase, and expose stored row IDs/payment links. Exception class assertions are represented by the production exception handler HTTP status, with original message assertions preserved.

| Original class and method | Cypress spec / test title |
| --- | --- |
| SaleServiceTest.pricesEveryLineFromTheMasterAndIssuesAReceipt | e2e/sale-rules.cy.js — pricesEveryLineFromTheMasterAndIssuesAReceipt |
| SaleServiceTest.receiptNumberCarriesYearBranchAndMonth | e2e/sale-rules.cy.js — receiptNumberCarriesYearBranchAndMonth |
| SaleServiceTest.multipliesUnitPriceByQuantity | e2e/sale-rules.cy.js — multipliesUnitPriceByQuantity |
| SaleServiceTest.acceptsPaymentSplitAcrossSeveralMethods | e2e/sale-rules.cy.js — acceptsPaymentSplitAcrossSeveralMethods |
| SaleServiceTest.rejectsPaymentsThatDoNotAddUpToTheTotal | e2e/sale-rules.cy.js — rejectsPaymentsThatDoNotAddUpToTheTotal |
| SaleServiceTest.rejectsOverpayment | e2e/sale-rules.cy.js — rejectsOverpayment |
| SaleServiceTest.sellingACourseCreditsItsSessionsImmediately | e2e/sale-rules.cy.js — sellingACourseCreditsItsSessionsImmediately |
| SaleServiceTest.sellingACourseAndUsingItOnTheSameDayWorks | e2e/sale-rules.cy.js — sellingACourseAndUsingItOnTheSameDayWorks |
| SaleServiceTest.sellsAServiceAndACourseOnTheSameReceipt | e2e/sale-rules.cy.js — sellsAServiceAndACourseOnTheSameReceipt |
| SaleServiceTest.storesTheSalespersonSeparatelyFromTheTreatingStaff | e2e/sale-rules.cy.js — storesTheSalespersonSeparatelyFromTheTreatingStaff |
| SaleServiceTest.rejectsSellingMoreThanOneCourseOnASingleLine | e2e/sale-rules.cy.js — rejectsSellingMoreThanOneCourseOnASingleLine |
| SaleServiceTest.rejectsAnInactiveCourse | e2e/sale-rules.cy.js — rejectsAnInactiveCourse |
| SaleServiceTest.rejectsAServiceThatHasNoPriceYet | e2e/sale-rules.cy.js — rejectsAServiceThatHasNoPriceYet |
| SaleServiceTest.rejectsAnUnknownPaymentMethod | e2e/sale-rules.cy.js — rejectsAnUnknownPaymentMethod |
| SaleServiceTest.voidingReversesTheRevenueAndCancelsTheCourseItSold | e2e/sale-rules.cy.js — voidingReversesTheRevenueAndCancelsTheCourseItSold |
| SaleServiceTest.voidingKeepsSessionsThePatientAlreadyUsed | e2e/sale-rules.cy.js — voidingKeepsSessionsThePatientAlreadyUsed |
| SaleServiceTest.voidingDeletesNothing | e2e/sale-rules.cy.js — voidingDeletesNothing |
| SaleServiceTest.rejectsVoidingTheSameReceiptTwice | e2e/sale-rules.cy.js — rejectsVoidingTheSameReceiptTwice |
| SaleServiceTest.voidingSkipsACourseThatWasAlreadyCancelledOnItsOwn | e2e/sale-rules.cy.js — voidingSkipsACourseThatWasAlreadyCancelledOnItsOwn |
| SaleServiceTest.listsReceiptsOfThePatientIncludingVoidedOnes | e2e/sale-rules.cy.js — listsReceiptsOfThePatientIncludingVoidedOnes |
| SaleServiceTest.listShowsMoneyActuallyKeptSoAVoidedReceiptReadsZero | e2e/sale-rules.cy.js — listShowsMoneyActuallyKeptSoAVoidedReceiptReadsZero |
| SaleServiceTest.listsEveryPatientWhenNoFilterIsGiven | e2e/sale-rules.cy.js — listsEveryPatientWhenNoFilterIsGiven |
| SaleServiceTest.rejectsAnOversizedPage | e2e/sale-rules.cy.js — rejectsAnOversizedPage |
| SaleServiceTest.rejectsAnUnknownPatientFilter | e2e/sale-rules.cy.js — rejectsAnUnknownPatientFilter |
| CourseLedgerServiceTest.purchaseCreditsSessionsPlusBonus | e2e/course-rules.cy.js — purchaseCreditsSessionsPlusBonus |
| CourseLedgerServiceTest.purchaseSnapshotsCourseMasterSoLaterEditsDoNotChangeSoldPackages | e2e/course-rules.cy.js — purchaseSnapshotsCourseMasterSoLaterEditsDoNotChangeSoldPackages |
| CourseLedgerServiceTest.expiryIsCalculatedFromValidityDays | e2e/course-rules.cy.js — expiryIsCalculatedFromValidityDays |
| CourseLedgerServiceTest.buyingAndUsingOnTheSameDayWorks | e2e/course-rules.cy.js — buyingAndUsingOnTheSameDayWorks |
| CourseLedgerServiceTest.balanceIsTheSumOfMovementsNotAStoredCounter | e2e/course-rules.cy.js — balanceIsTheSumOfMovementsNotAStoredCounter |
| CourseLedgerServiceTest.rejectsUsageWhenNoSessionsLeft | e2e/course-rules.cy.js — rejectsUsageWhenNoSessionsLeft |
| CourseLedgerServiceTest.rejectsUsageOfExpiredCourse | e2e/course-rules.cy.js — rejectsUsageOfExpiredCourse |
| CourseLedgerServiceTest.reversingUsageGivesTheSessionBackAndKeepsTheOriginalRow | e2e/course-rules.cy.js — reversingUsageGivesTheSessionBackAndKeepsTheOriginalRow |
| CourseLedgerServiceTest.rejectsReversingTheSameMovementTwice | e2e/course-rules.cy.js — rejectsReversingTheSameMovementTwice |
| CourseLedgerServiceTest.rejectsReversingAPurchaseMovement | e2e/course-rules.cy.js — rejectsReversingAPurchaseMovement |
| CourseLedgerServiceTest.cancellingWritesOffTheRemainingBalanceButKeepsUsedSessions | e2e/course-rules.cy.js — cancellingWritesOffTheRemainingBalanceButKeepsUsedSessions |
| CourseLedgerServiceTest.rejectsUsageAfterCancellation | e2e/course-rules.cy.js — rejectsUsageAfterCancellation |
| CourseLedgerServiceTest.rejectsCancellingTwice | e2e/course-rules.cy.js — rejectsCancellingTwice |
| CourseLedgerServiceTest.nothingIsEverDeletedFromTheLedger | e2e/course-rules.cy.js — nothingIsEverDeletedFromTheLedger |
| CourseLedgerServiceTest.listsCoursesOfThePatient | e2e/course-rules.cy.js — listsCoursesOfThePatient |
