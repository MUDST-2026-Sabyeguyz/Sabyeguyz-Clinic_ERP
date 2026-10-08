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
  it('rejectsLinkingAPatientToAnAppointmentThatAlreadyHasOne', () => rejected('appointment', 'link-patient-already-linked'))
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
})

describe('clinicv2 CourseTransferServiceTest migrated assertions', () => {
  it('transfersVisitsAndWritesBothLedgerEntries', () => {
    value('transfer', 'success').should('include', { qtyTransferred: 4, ledgerSaves: 2, memberSaves: 1 })
  })
  it('rejectsTransferExceedingTheBalance', () => rejected('transfer', 'insufficient', 'ไม่พอ'))
  it('rejectsTransferringToOneself', () => rejected('transfer', 'self'))
})

describe('clinicv2 CourseVisitUsageServiceTest migrated assertions', () => {
  it('caseOwnerTreatingThemselvesGetsTheFullAllocation', () => {
    value('usage', 'owner').should('include', { gross: 70, fee: 0, ownerNet: 70, remainingVisits: 9, visitsUsed: 1 })
  })
  it('substituteFixedFeeMatchesCase3And9', () => {
    value('usage', 'fixed').should('include', { gross: 70, fee: 45, ownerNet: 25 })
  })
  it('substitutePercentageFeeMatchesCase10', () => {
    value('usage', 'percentage').should('include', { fee: 30, ownerNet: 40 })
  })
  it('feeExceedingAllocationIsCappedAndOwnerNetNeverGoesNegative', () => {
    value('usage', 'capped').should('include', { fee: 40, ownerNet: 0 })
  })
  it('rejectsUsageOnAnExpiredCourse', () => rejected('usage', 'expired', 'หมดอายุ'))
  it('rejectsUsageWhenCourseHasNoRemainingVisits', () => rejected('usage', 'exhausted'))
  it('rejectsUsageWhenThisPatientHasNoBalanceEvenIfCourseDoes', () => rejected('usage', 'no-balance'))
})
