// Original clinicv2 service cases; all behavioral assertions execute in Cypress.
function scenario(group, name) {
  return cy.api('GET', `/__cypress/v2/workflows/${group}/${name}`).then(({ status, body }) => {
    expect(status).to.equal(200)
    return body
  })
}
function value(group, name) {
  return scenario(group, name).then((body) => {
    expect(body).not.to.have.property('error')
    expect(body).to.have.property('value')
    return body.value
  })
}
function rejected(group, name, message) {
  return scenario(group, name).then((body) => {
    expect(body.error).to.equal('IllegalArgumentException')
    if (message) expect(body.errorMessage).to.include(message)
  })
}

describe('clinicv2 AppointmentServiceTest migrated assertions', () => {
  it('booksAConfirmedAppointment', () => {
    value('appointment', 'book').its('status').should('equal', 'Confirmed')
  })
  it('rejectsBookingWhenStaffAlreadyBooked', () => rejected('appointment', 'conflict'))
  it('booksAnUnconfirmedAppointment', () => {
    value('appointment', 'book-unconfirmed').should('include', { status: 'Unconfirmed', patientType: 'CURRENT', hasPatient: true, historySaves: 0 })
  })
  it('rejectsAnInitialStatusOtherThanUnconfirmedOrConfirmed', () => rejected('appointment', 'book-bad-initial-status'))
  it('booksANewPatientByNameWithoutAPatientRecord', () => {
    value('appointment', 'book-new-patient').should('include', {
      status: 'Unconfirmed', patientType: 'NEW', hasPatient: false, contactName: 'สมชาย ใจดี',
    })
  })
  it('booksANewPatientByNicknameWithPhone', () => {
    value('appointment', 'new-nickname-with-phone').should('include', {
      status: 'Confirmed', patientType: 'NEW', contactName: 'แบงค์', contactPhone: '0812345678',
    })
  })
  it('rejectsANewPatientNicknameWithoutPhone', () => rejected('appointment', 'new-nickname-without-phone', 'เบอร์โทร'))
  it('rejectsANewPatientWithoutAName', () => rejected('appointment', 'new-without-name'))
  it('confirmMovesUnconfirmedToConfirmedAndRecordsHistory', () => {
    value('appointment', 'confirm').should('include', { status: 'Confirmed', historySaves: 1 })
  })
  it('rejectsConfirmingAnAppointmentThatIsNotUnconfirmed', () => rejected('appointment', 'confirm-already-confirmed'))
  it('registeringANewPatientMakesTheAppointmentCurrent', () => {
    value('appointment', 'link-patient').should('include', { patientType: 'CURRENT', hasPatient: true })
  })
  it('rejectsLinkingAPatientToAnAppointmentThatAlreadyHasOne', () => rejected('appointment', 'link-patient-already-linked', 'ลงทะเบียนแล้ว'))
  it('rejectsCheckInOfAnUnconfirmedAppointment', () => rejected('appointment', 'arrive-unconfirmed', 'ยืนยัน'))
  it('rejectsCheckInOfANewPatientWhoIsNotRegistered', () => rejected('appointment', 'arrive-new-patient', 'ลงทะเบียน'))
  it('cancelRecordsStatusHistory', () => {
    value('appointment', 'cancel').should('include', { status: 'Cancelled', historySaves: 1 })
  })
  it('rejectsCancelOnAlreadyCompletedAppointment', () => rejected('appointment', 'completed'))
  it('rescheduleMovesDateAndTimeAndKeepsConfirmedStatus', () => {
    value('appointment', 'reschedule').should('include', {
      date: '2026-12-05', time: '10:00:00', status: 'Confirmed',
    })
  })
})

describe('clinicv2 PatientVisitServiceTest migrated assertions', () => {
  it('checkInFromAppointmentMarksTheAppointmentArrived', () => {
    value('visit', 'check-in').then((body) => {
      expect(body.status).to.equal('Arrived')
      expect(body.sameAppointment).to.equal(true)
      expect(body.arrivedCalls).to.deep.equal([[5, 1]])
    })
  })
  it('checkInWalkInRequiresAPatientAndBranch', () => rejected('visit', 'missing-patient'))
  it('completeSetsCheckoutTimeAndPropagatesToTheAppointment', () => {
    value('visit', 'complete').then((body) => {
      expect(body.status).to.equal('Completed')
      expect(body.checkoutTime).not.to.be.null
      expect(body.completedCalls).to.deep.equal([[null, 1]])
    })
  })
  it('rejectsProgressingACancelledVisit', () => rejected('visit', 'cancelled'))
})

describe('clinicv2 AuditLogServiceTest migrated assertions', () => {
  it('recordsAnAuditEntry', () => {
    value('audit', 'record').should('include', { tableName: 'branch', action: 'UPDATE' })
  })
  it('rejectsMissingAction', () => rejected('audit', 'missing-action'))
  // Data Dictionary Audit_log: JSON values, changed_by only for a user, source for an automatic event
  it('keepsPlainTextAsJson', () => {
    value('audit', 'plain-text').then((log) => {
      expect(JSON.parse(log.oldValue)).to.deep.equal({ summary: 'Active' })
      expect(JSON.parse(log.newValue)).to.deep.equal({ summary: 'Cancelled' })
      expect(log.changedBy).to.equal(1)
    })
  })
  it('namesTheSourceOfAnAutomaticEventWithoutAUser', () => {
    value('audit', 'automatic').then((log) => {
      expect(log.changedBy).to.equal(null)
      expect(JSON.parse(log.newValue)).to.deep.equal({ status: 'Cancelled', source: 'System' })
    })
    value('audit', 'google').then((log) => {
      expect(log.changedBy).to.equal(null)
      expect(JSON.parse(log.newValue)).to.deep.equal({ source: 'GoogleCalendar', appointment_status: 'Cancelled' })
      expect(JSON.parse(log.oldValue)).to.deep.equal({ appointment_status: 'Confirmed' })
    })
  })
  it('storesBeforeAndAfterAsJsonForAUserChange', () => {
    value('audit', 'change').then((log) => {
      expect(JSON.parse(log.oldValue)).to.deep.equal({ effective_to: null })
      expect(JSON.parse(log.newValue)).to.deep.equal({ effective_to: '2026-10-31', reason: 'ended by the new version' })
      expect(log.changedBy).to.equal(1)
    })
  })
})

describe('clinicv2 CourseTransferServiceTest migrated assertions', () => {
  it('transfersVisitsAndWritesBothLedgerEntries', () => {
    value('transfer', 'success').should('include', { qtyTransferred: 4, ledgerSaves: 2, memberSaves: 1 })
  })
  it('transferLinksBothLedgerEntriesToTheTransferAndMovesTheEntitlements', () => {
    value('transfer', 'success').should('include', {
      status: 'Completed', ledgerLinked: true, entitlementSaves: 3, fromAllocated: 2, toAllocated: 4,
    })
    value('transfer', 'success').then((body) => {
      expect(body.ledgerTypes).to.deep.equal(['TransferOut', 'TransferIn'])
    })
  })
  it('reversingATransferWritesTwoReversalEntriesAndRecordsTheCancellation', () => {
    value('transfer', 'reverse').then((body) => {
      expect(body.status).to.equal('Reversed')
      expect(body.ledgerTypes).to.deep.equal(['Reversal', 'Reversal'])
      expect(body.qtyChanges).to.deep.equal([4, -4])
      expect(body.balancesAfter).to.deep.equal([6, 0])
      expect(body.reversalsLinked).to.equal(true)
      expect(body.cancellationType).to.equal('TransferReversal')
      expect(body.cancellationTargetsTransfer).to.equal(true)
      expect(body.entitlementSaves).to.equal(2)
    })
  })
  it('rejectsReversingATransferWhoseSessionsTheReceiverAlreadyUsed', () => rejected('transfer', 'reverse-receiver-used', 'ใช้สิทธิ์ไปแล้ว'))
  it('rejectsReversingATransferTwice', () => rejected('transfer', 'reverse-twice'))
  it('rejectsReversingATransferWithoutAReason', () => rejected('transfer', 'reverse-no-reason'))
  it('rejectsTransferExceedingTheBalance', () => rejected('transfer', 'insufficient', 'ไม่พอ'))
  it('rejectsTransferringToOneself', () => rejected('transfer', 'self'))
})

describe('clinicv2 CourseVisitUsageServiceTest migrated assertions', () => {
  it('caseOwnerTreatingThemselvesGetsTheFullAllocation', () => {
    value('usage', 'owner').should('include', { gross: 70, fee: 0, ownerNet: 70, remainingVisits: 9, visitsUsed: 1 })
  })
  it('substituteFixedFeeMatchesCase3And9', () => {
    value('usage', 'fixed').should('include', { gross: 45, fee: 45, ownerNet: 0 })
  })
  it('substitutePercentageFeeMatchesCase10', () => {
    value('usage', 'percentage').should('include', { fee: 30, ownerNet: 0 })
  })
  it('feeExceedingTheAllocationIsPaidInFullAndTheOwnerGetsNothing', () => {
    value('usage', 'capped').should('include', { fee: 60, ownerNet: 0 })
  })
  it('usageIsCompletedNeedsAPrintLinksTheLedgerAndUpdatesTheEntitlement', () => {
    value('usage', 'owner').should('include', {
      usageStatus: 'Completed', allocationType: 'Allocation', ledgerLinkedToUsage: true,
      entitlementUsed: 1, entitlementRemaining: 9,
    })
  })
  it('reversingAUsageRestoresTheSessionAndReversesTheCommissionExactly', () => {
    value('usage', 'reverse').then((body) => {
      expect(body.usageStatus).to.equal('Reversed')
      expect(body.allocationTypes).to.deep.equal(['Allocation', 'Reversal'])
      expect(body.reversalGross).to.equal(-70)
      expect(body.reversalFee).to.equal(0)
      expect(body.reversalOwnerNet).to.equal(-70)
      expect(body.reversalLinked).to.equal(true)
      expect(body.ledgerTypes).to.deep.equal(['Treatment', 'Reversal'])
      expect(body.ledgerReversalLinked).to.equal(true)
      expect(body.reversalQty).to.equal(1)
      expect(body.visitsUsed).to.equal(0)
      expect(body.remainingVisits).to.equal(10)
      expect(body.ownerNetTotal).to.equal(0)
      expect(body.feeTotal).to.equal(0)
      expect(body.outstanding).to.equal(700)
      expect(body.entitlementUsed).to.equal(0)
      expect(body.cancellationType).to.equal('UsageReversal')
    })
  })
  it('reversingASubstituteUsageReversesTheTreatmentFeeToo', () => {
    value('usage', 'reverse-substitute').should('include', {
      reversalGross: -45, reversalFee: -45, reversalOwnerNet: 0, feeTotal: 0, ownerNetTotal: 0, outstanding: 700,
    })
  })
  it('rejectsReversingAUsageTwice', () => rejected('usage', 'reverse-twice'))
  it('rejectsReversingAUsageWithoutAReason', () => rejected('usage', 'reverse-no-reason'))
  it('rejectsUsageOnAnExpiredCourse', () => rejected('usage', 'expired', 'หมดอายุ'))
  it('rejectsUsageWhenCourseHasNoRemainingVisits', () => rejected('usage', 'exhausted'))
  it('rejectsUsageWhenThisPatientHasNoBalanceEvenIfCourseDoes', () => rejected('usage', 'no-balance'))
})

describe('clinicv2 CourseUsagePrintService assertions', () => {
  it('issuingCreatesVersionOneWithAFrozenSnapshotAndAnIssuedEvent', () => {
    value('print', 'issue').should('include', {
      version: 1, eventType: 'Issued', issuedBy: 9, sameUsage: true, snapshotHasCourseNo: true,
    })
  })
  it('issuingAgainCreatesTheNextVersion', () => {
    value('print', 'issue-second-version').should('include', { version: 2, eventType: 'Issued' })
  })
  it('rejectsPrintingAReversedUsage', () => rejected('print', 'issue-reversed-usage'))
  it('reprintingAddsAReprintEventToTheSameDocument', () => {
    value('print', 'reprint').should('include', { eventType: 'Reprint', printedBy: 9, sameDocument: true })
  })
  it('rejectsReprintingAMissingDocument', () => rejected('print', 'reprint-missing'))
})
