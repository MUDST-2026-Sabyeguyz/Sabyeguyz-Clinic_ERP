# Identity and concurrency migration

Every method below is a Cypress test with the original method name as its title. All assertions run in Cypress. HN format/assignment operations use focused Java helpers because those operations have no public API. Authentication uses real stored users and hash observation; username/email priority is verified with conflicting identifiers instead of mocked repository calls. Catalog and registration use public APIs. Concurrency uses two independent HTTP requests released together by a Node task and checks committed state afterward.

| Original method | Cypress spec |
| --- | --- |
| AuthServiceTest.registerHashesPasswordAndDefaultsRole | identity-rules.cy.js |
| AuthServiceTest.loginRejectsWrongPassword | identity-rules.cy.js |
| AuthServiceTest.loginChecksUsernameBeforeEmail | identity-rules.cy.js |
| AuthServiceTest.loginFallsBackToEmail | identity-rules.cy.js |
| HnGeneratorTest.formatsYearBranchMonthAndRunningNumber | identity-rules.cy.js |
| HnGeneratorTest.monthIsPartOfHnAndNaturallyResetsCounterScope | identity-rules.cy.js |
| HnGeneratorTest.patientHnIsImmutableAfterFirstAssignment | identity-rules.cy.js |
| HnGeneratorTest.rejectsUnresolvedOrNonNumericBranchCode | identity-rules.cy.js |
| PatientRegistrationServiceTest.persistsPatientAndGeneratesBranchHn | identity-rules.cy.js |
| PatientRegistrationServiceTest.incrementsRunningNumberForSameBranchAndMonth | identity-rules.cy.js |
| CourseRepositoryTest.courseKeepsConfiguredPackageValues | identity-rules.cy.js |
| AppointmentConcurrencyTest.allowsOnlyOneBookingWhenTwoRequestsRaceForTheSameSlot | concurrency.cy.js |
| AppointmentConcurrencyTest.allowsOnlyOneRescheduleWhenTwoAppointmentsRaceForTheSameSlot | concurrency.cy.js |
| AppointmentConcurrencyTest.allowsOnlyOneCheckInWhenTwoRequestsRace | concurrency.cy.js |
| AppointmentConcurrencyTest.keepsCancellationConsistentWhenCheckInAndCancelRace | concurrency.cy.js |
| AppointmentConcurrencyTest.keepsStateConsistentWhenRescheduleAndCancelRace | concurrency.cy.js |
| CourseLedgerConcurrencyTest.allowsOnlyOneUsageWhenTwoRequestsRaceForTheLastSession | concurrency.cy.js |
| CourseLedgerConcurrencyTest.doesNotDoubleReverseWhenTwoRequestsRaceOnTheSameUsage | concurrency.cy.js |
| SaleConcurrencyTest.neverIssuesTheSameReceiptNumberTwice | concurrency.cy.js |
| SaleConcurrencyTest.doesNotRefundTwiceWhenTwoRequestsVoidTheSameReceipt | concurrency.cy.js |
