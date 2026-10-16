# ReportServiceTest migration

All 29 original scenarios run and assert in Cypress via production report HTTP endpoints.
Fixture helpers only create catalog/staff/branch data or shift persisted receipt/purchase dates.
Appointments and course usage/reversals are performed through production HTTP APIs.

| Original Java method | Cypress spec / test title |
| --- | --- |
| `ReportServiceTest.totalRevenueEqualsTheSumOfTheCategoryBreakdown` | `cypress/e2e/report-rules.cy.js` → `totalRevenueEqualsTheSumOfTheCategoryBreakdown` |
| `ReportServiceTest.splitsRevenueBetweenCourseAndSingleVisit` | `cypress/e2e/report-rules.cy.js` → `splitsRevenueBetweenCourseAndSingleVisit` |
| `ReportServiceTest.aVoidedReceiptContributesNothingToRevenue` | `cypress/e2e/report-rules.cy.js` → `aVoidedReceiptContributesNothingToRevenue` |
| `ReportServiceTest.reportsZeroRatherThanNullWhenThereIsNoActivity` | `cypress/e2e/report-rules.cy.js` → `reportsZeroRatherThanNullWhenThereIsNoActivity` |
| `ReportServiceTest.categorySharesAddUpToOne` | `cypress/e2e/report-rules.cy.js` → `categorySharesAddUpToOne` |
| `ReportServiceTest.dailySeriesCoversEveryDayIncludingDaysWithNoSales` | `cypress/e2e/report-rules.cy.js` → `dailySeriesCoversEveryDayIncludingDaysWithNoSales` |
| `ReportServiceTest.bucketsALateEveningSaleOnTheClinicDayNotTheUtcDay` | `cypress/e2e/report-rules.cy.js` → `bucketsALateEveningSaleOnTheClinicDayNotTheUtcDay` |
| `ReportServiceTest.excludesSalesOutsideTheRequestedPeriod` | `cypress/e2e/report-rules.cy.js` → `excludesSalesOutsideTheRequestedPeriod` |
| `ReportServiceTest.includesSalesOnBothEdgesOfTheRange` | `cypress/e2e/report-rules.cy.js` → `includesSalesOnBothEdgesOfTheRange` |
| `ReportServiceTest.rejectsAnInvertedDateRange` | `cypress/e2e/report-rules.cy.js` → `rejectsAnInvertedDateRange` |
| `ReportServiceTest.rejectsARangeLongerThanTwoYears` | `cypress/e2e/report-rules.cy.js` → `rejectsARangeLongerThanTwoYears` |
| `ReportServiceTest.rejectsAnUnknownBranch` | `cypress/e2e/report-rules.cy.js` → `rejectsAnUnknownBranch` |
| `ReportServiceTest.filtersRevenueByBranch` | `cypress/e2e/report-rules.cy.js` → `filtersRevenueByBranch` |
| `ReportServiceTest.breaksRevenueDownByBranch` | `cypress/e2e/report-rules.cy.js` → `breaksRevenueDownByBranch` |
| `ReportServiceTest.countsOnlyAppointmentsThePatientActuallyAttended` | `cypress/e2e/report-rules.cy.js` → `countsOnlyAppointmentsThePatientActuallyAttended` |
| `ReportServiceTest.splitsAttendingPatientsByGender` | `cypress/e2e/report-rules.cy.js` → `splitsAttendingPatientsByGender` |
| `ReportServiceTest.countsAppointmentsByStatus` | `cypress/e2e/report-rules.cy.js` → `countsAppointmentsByStatus` |
| `ReportServiceTest.measuresNoShowRateAgainstAppointmentsThatCameDueOnly` | `cypress/e2e/report-rules.cy.js` → `measuresNoShowRateAgainstAppointmentsThatCameDueOnly` |
| `ReportServiceTest.reportsZeroNoShowRateWhenNothingHasComeDue` | `cypress/e2e/report-rules.cy.js` → `reportsZeroNoShowRateWhenNothingHasComeDue` |
| `ReportServiceTest.breaksAppointmentsDownByTherapistWithTheirVisitCount` | `cypress/e2e/report-rules.cy.js` → `breaksAppointmentsDownByTherapistWithTheirVisitCount` |
| `ReportServiceTest.fillsEveryDayOfTheAppointmentSeries` | `cypress/e2e/report-rules.cy.js` → `fillsEveryDayOfTheAppointmentSeries` |
| `ReportServiceTest.keepsReceiptsWithoutASalespersonInTheirOwnBucket` | `cypress/e2e/report-rules.cy.js` → `keepsReceiptsWithoutASalespersonInTheirOwnBucket` |
| `ReportServiceTest.perPersonRowsPlusUnassignedEqualTheBranchTotal` | `cypress/e2e/report-rules.cy.js` → `perPersonRowsPlusUnassignedEqualTheBranchTotal` |
| `ReportServiceTest.ranksSalespeopleByAmountSoldDescending` | `cypress/e2e/report-rules.cy.js` → `ranksSalespeopleByAmountSoldDescending` |
| `ReportServiceTest.leavesVoidedReceiptsOutOfStaffSales` | `cypress/e2e/report-rules.cy.js` → `leavesVoidedReceiptsOutOfStaffSales` |
| `ReportServiceTest.reportsPurchasedUsedAndRemainingPerCourse` | `cypress/e2e/report-rules.cy.js` → `reportsPurchasedUsedAndRemainingPerCourse` |
| `ReportServiceTest.purchasedAlwaysEqualsUsedPlusRemainingForAnActiveCourse` | `cypress/e2e/report-rules.cy.js` → `purchasedAlwaysEqualsUsedPlusRemainingForAnActiveCourse` |
| `ReportServiceTest.keepsACancelledCourseVisibleButOutOfTheSoldValue` | `cypress/e2e/report-rules.cy.js` → `keepsACancelledCourseVisibleButOutOfTheSoldValue` |
| `ReportServiceTest.filtersCourseBalancesByPatient` | `cypress/e2e/report-rules.cy.js` → `filtersCourseBalancesByPatient` |
