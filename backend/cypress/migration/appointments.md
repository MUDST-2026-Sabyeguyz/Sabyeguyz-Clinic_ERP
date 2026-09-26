# Appointment migration

All assertions live in `cypress/e2e/appointment-rules.cy.js`. Original method names are the exact Cypress test titles within the matching class-named describe block.

State unit tests and service integration tests now use authenticated real HTTP APIs with persisted entities. Exception assertions become the corresponding 400/404/409 HTTP status and preserve original message fragments. No mocks or Java assertions remain.

Repository tests seed real appointments through public HTTP and invoke the actual repository overlap methods using the test-only helper, preserving exact query intervals including contained/enclosing ranges and excluded appointment IDs. The boolean returned is the repository operation result, not a test verdict. State enum membership and updatedAt use read-only internal access because those fields are absent from the public API. Relative future dates replace fixed historical fixture dates.

| Original class.method | Cypress location / level |
| --- | --- |
| AppointmentStateTest.newAppointmentStartsAsConfirmed | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.checkInMovesConfirmedToArrived | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.startServiceMovesArrivedToInService | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.completeMovesInServiceToCompleted | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rescheduleMovesConfirmedToRescheduled | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rescheduledAppointmentCanBeRescheduledAgain | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rescheduledAppointmentCanCheckIn | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.noShowMovesConfirmedToNoShow | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.cancelStoresReason | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.cancelIsAllowedAfterArrival | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsStartServiceBeforeArrival | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsCompleteBeforeServiceStarted | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsSecondCheckIn | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsCancelAfterCompleted | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsCancelAfterServiceStarted | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsCheckInAfterCancel | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsNoShowAfterArrival | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.rejectsRescheduleAfterArrival | appointment-rules.cy.js / real HTTP integration |
| AppointmentStateTest.cancelledAndNoShowReleaseTheTimeSlot | appointment-rules.cy.js / internal enum via read-only helper |
| AppointmentStateTest.updatesTimestampOnTransition | appointment-rules.cy.js / HTTP lifecycle; timestamp via read-only helper |
| AppointmentBookingServiceTest.createsConfirmedAppointmentUsingServiceDuration | appointment-rules.cy.js / real HTTP integration |
| AppointmentBookingServiceTest.rejectsOverlappingAppointmentForSameTherapist | appointment-rules.cy.js / real HTTP integration |
| AppointmentBookingServiceTest.allowsBackToBackAppointmentForSameTherapist | appointment-rules.cy.js / real HTTP integration |
| AppointmentBookingServiceTest.rejectsStartTimeInThePast | appointment-rules.cy.js / real HTTP integration |
| AppointmentBookingServiceTest.rejectsUnknownPatient | appointment-rules.cy.js / real HTTP integration |
| AppointmentBookingServiceTest.rejectsInactiveService | appointment-rules.cy.js / real HTTP integration |
| AppointmentBookingServiceTest.rejectsTherapistFromAnotherBranch | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.reschedulesToNewTimeKeepingSameDuration | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.reschedulesToDifferentTherapistInSameBranch | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.keepsOriginalTherapistWhenNotProvided | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.allowsReschedulingOntoItsOwnOriginalSlot | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.rejectsRescheduleOfArrivedAppointment | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.rejectsRescheduleOntoOverlappingSlot | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.allowsReschedulingBackToBackWithAnotherAppointment | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.rejectsRescheduleToPastTime | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.rejectsInactiveTherapist | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.rejectsTherapistFromAnotherBranch | appointment-rules.cy.js / real HTTP integration |
| AppointmentRescheduleServiceTest.rejectsRescheduleOfMissingAppointment | appointment-rules.cy.js / real HTTP integration |
| AppointmentRepositoryTest.detectsPartialOverlap | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.detectsFullyContainedAppointment | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.detectsEnclosingAppointment | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.allowsBackToBackAppointmentAfter | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.allowsBackToBackAppointmentBefore | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.allowsSameTimeForAnotherTherapist | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.ignoresCancelledAppointments | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.ignoresNoShowAppointments | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.stillDetectsRescheduledAppointments | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.excludingAppointmentAllowsRescheduleOntoSameSlot | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentRepositoryTest.excludingAppointmentStillDetectsOtherOverlaps | appointment-rules.cy.js / real repository via helper; HTTP fixture setup |
| AppointmentQueryServiceTest.returnsAppointmentsOfTheDaySortedByStartTime | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.filtersByTherapist | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.filtersByStatus | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.returnsEmptyPageForDayWithoutAppointments | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.paginatesInDatabase | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.rejectsInvalidPagination | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.rejectsUnknownBranchCode | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.returnsAppointmentById | appointment-rules.cy.js / real HTTP integration |
| AppointmentQueryServiceTest.throwsWhenAppointmentDoesNotExist | appointment-rules.cy.js / real HTTP integration |
