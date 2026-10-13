// Assertions migrated one-for-one from the clinicv2 core JUnit suites.
// Java adapters execute production services and expose observations only.
describe('Clinic v2 core service scenarios', () => {
  it('AppUserService: registerHashesThePassword', () => {
    cy.api('POST', '/__cypress/v2/core/AppUserService/registerHashesThePassword').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.username).to.equal("alice")
      expect(body.passwordHash).not.to.equal("password123")
      expect(body.userStatus).to.equal(true)
    })
  })

  it('AppUserService: rejectsShortPassword', () => {
    cy.api('POST', '/__cypress/v2/core/AppUserService/rejectsShortPassword').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
      expect(body.usersSaveCalls).to.equal(0)
    })
  })

  it('AppUserService: loginRejectsWrongPassword', () => {
    cy.api('POST', '/__cypress/v2/core/AppUserService/loginRejectsWrongPassword').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('AppUserService: loginRejectsDeactivatedUser', () => {
    cy.api('POST', '/__cypress/v2/core/AppUserService/loginRejectsDeactivatedUser').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('AppUserService: grantBranchAccessIsIdempotent', () => {
    cy.api('POST', '/__cypress/v2/core/AppUserService/grantBranchAccessIsIdempotent').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.userBranchAccessSaveCalls).to.equal(0)
    })
  })

  it('AppUserService: assignRoleSavesWhenNotAlreadyAssigned', () => {
    cy.api('POST', '/__cypress/v2/core/AppUserService/assignRoleSavesWhenNotAlreadyAssigned').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.userRolesSaveCalls).to.equal(1)
    })
  })

  it('BranchService: createsBranchWithValidTwoDigitCode', () => {
    cy.api('POST', '/__cypress/v2/core/BranchService/createsBranchWithValidTwoDigitCode').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.branchCode).to.equal("01")
      expect(body.branchStatus).to.equal(true)
      expect(body.branchCreateBy).to.equal(1)
    })
  })

  it('BranchService: rejectsNonNumericBranchCode', () => {
    cy.api('POST', '/__cypress/v2/core/BranchService/rejectsNonNumericBranchCode').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
      expect(body.repositorySaveCalls).to.equal(0)
    })
  })

  it('BranchService: rejectsDuplicateBranchCode', () => {
    cy.api('POST', '/__cypress/v2/core/BranchService/rejectsDuplicateBranchCode').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
      expect(body.errorMessage).to.include("ซ้ำ")
    })
  })

  it('BranchService: rejectsBlankAddress', () => {
    cy.api('POST', '/__cypress/v2/core/BranchService/rejectsBlankAddress').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('BranchService: updateNeverTouchesBranchCode', () => {
    cy.api('POST', '/__cypress/v2/core/BranchService/updateNeverTouchesBranchCode').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.branchCode).to.equal("02")
      expect(body.branchName).to.equal("ชื่อใหม่")
      expect(body.branchUpdateBy).to.equal(2)
    })
  })

  it('BranchService: deactivateThrowsWhenBranchMissing', () => {
    cy.api('POST', '/__cypress/v2/core/BranchService/deactivateThrowsWhenBranchMissing').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('BranchNotFoundException')
    })
  })

  it('CourseService: sellsACourseWithAProvisionalTierRate', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/sellsACourseWithAProvisionalTierRate').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.courseNo).to.equal("RE22063150")
      expect(body.coursePrice).to.equal(Number("10000.00"))
      expect(body.totalVisits).to.equal(10)
      expect(body.remainingVisits).to.equal(10)
      expect(body.provisionalCommissionRate).to.equal(Number("0.07"))
      expect(body.commissionStatus).to.equal("Provisional")
      expect(body.courseStatus).to.equal("Active")
      expect(body.caseOwnerIsSeller).to.equal(true)
      expect(body.expiryDate).to.equal("2026-11-16")
    })
  })

  it('CourseService: appendsSequenceSuffixWhenMultipleCoursesShareAReceipt', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/appendsSequenceSuffixWhenMultipleCoursesShareAReceipt').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.courseNo).to.equal("RE22063150-02")
      expect(body.courseSequence).to.equal(2)
    })
  })

  it('CourseService: remainingVisitsIncludesBonusButCommissionRateIgnoresIt', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/remainingVisitsIncludesBonusButCommissionRateIgnoresIt').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.totalVisits).to.equal(13)
      expect(body.remainingVisits).to.equal(13)
    })
  })

  it('CourseService: rejectsACourseTemplateWithoutADefaultValidity', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/rejectsACourseTemplateWithoutADefaultValidity').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('CourseService: recordsThePurchaserTheSaleLineAndEntitledVisits', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/recordsThePurchaserTheSaleLineAndEntitledVisits').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.samePurchaser).to.equal(true)
      expect(body.sameSaleLine).to.equal(true)
      expect(body.entitledVisits).to.equal(13)
    })
  })

  it('CourseService: creditsTheSaleToTheSellerAtOneHundredPercentByDefault', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/creditsTheSaleToTheSellerAtOneHundredPercentByDefault').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).not.to.have.property('error')
      expect(body.sharesSaved).to.equal(1)
      expect(body.percent).to.equal(100)
      expect(body.credit).to.equal(10000)
      expect(body.salesSeenByTier).to.equal(10000)
    })
  })

  it('CourseService: splitsTheSaleCreditBetweenTwoStaff', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/splitsTheSaleCreditBetweenTwoStaff').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).not.to.have.property('error')
      expect(body.sharesSaved).to.equal(2)
      expect(body.firstCredit).to.equal(6000)
      expect(body.secondCredit).to.equal(4000)
      expect(body.salesSeenByTier).to.equal(6000)
    })
  })

  it('CourseService: createsTheOwnerMemberAndEntitlementForThePurchaser', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/createsTheOwnerMemberAndEntitlementForThePurchaser').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.memberRole).to.equal('Owner')
      expect(body.memberIsPurchaser).to.equal(true)
      expect(body.allocatedVisits).to.equal(13)
      expect(body.usedVisits).to.equal(0)
      expect(body.remainingVisits).to.equal(13)
    })
  })

  it('CourseService: recordsThePurchaseLedgerEntryAgainstTheSaleLine', () => {
    cy.api('POST', '/__cypress/v2/core/CourseService/recordsThePurchaseLedgerEntryAgainstTheSaleLine').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.entryType).to.equal('Purchase')
      expect(body.qtyChange).to.equal(13)
      expect(body.balanceAfter).to.equal(13)
      expect(body.sourceIsTheSaleLine).to.equal(true)
      expect(body.hasOtherSource).to.equal(false)
    })
  })

  const rejectedPurchases = [
    'rejectsASaleLineThatIsNotACoursePurchase',
    'rejectsASaleLineThatAlreadyHasACourse',
    'rejectsAPurchaserThatDiffersFromTheReceiptPatient',
    'rejectsSaleSharesThatDoNotTotalOneHundred',
    'rejectsTheSameStaffTwiceInTheSaleShares',
  ]
  rejectedPurchases.forEach((scenario) => {
    it(`CourseService: ${scenario}`, () => {
      cy.api('POST', `/__cypress/v2/core/CourseService/${scenario}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.error).to.equal('IllegalArgumentException')
      })
    })
  })

  it('ServiceCatalogService: createsASingleVisitService', () => {
    cy.api('POST', '/__cypress/v2/core/ServiceCatalogService/createsASingleVisitService').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.serviceCode).to.equal("PHYSIO_30")
      expect(body.serviceStatus).to.equal(true)
    })
  })

  it('ServiceCatalogService: courseRequiresADefaultTotalVisits', () => {
    cy.api('POST', '/__cypress/v2/core/ServiceCatalogService/courseRequiresADefaultTotalVisits').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('ServiceCatalogService: rejectsAnAssessmentService', () => {
    cy.api('POST', '/__cypress/v2/core/ServiceCatalogService/rejectsAnAssessmentService').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('ServiceCatalogService: rejectsNegativePrice', () => {
    cy.api('POST', '/__cypress/v2/core/ServiceCatalogService/rejectsNegativePrice').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('MasterDataService: createsAType', () => {
    cy.api('POST', '/__cypress/v2/core/MasterDataService/createsAType').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.typeName).to.equal("CUSTOMER_GROUP")
    })
  })

  it('MasterDataService: rejectsDuplicateTypeName', () => {
    cy.api('POST', '/__cypress/v2/core/MasterDataService/rejectsDuplicateTypeName').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('MasterDataService: createsAValueUnderAType', () => {
    cy.api('POST', '/__cypress/v2/core/MasterDataService/createsAValueUnderAType').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.valueName).to.equal("VIP")
      expect(body.valueStatus).to.equal(true)
    })
  })

  it('MasterDataService: deactivateThrowsWhenValueMissing', () => {
    cy.api('POST', '/__cypress/v2/core/MasterDataService/deactivateThrowsWhenValueMissing').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('HnGeneratorService: formatsYearBranchMonthAndRunningNumber', () => {
    cy.api('POST', '/__cypress/v2/core/HnGeneratorService/formatsYearBranchMonthAndRunningNumber').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.hn).to.equal("2601120001")
    })
  })

  it('HnGeneratorService: startsANewSequenceAtOne', () => {
    cy.api('POST', '/__cypress/v2/core/HnGeneratorService/startsANewSequenceAtOne').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.hn).to.equal("2601120001")
    })
  })

  it('HnGeneratorService: incrementsAnExistingSequence', () => {
    cy.api('POST', '/__cypress/v2/core/HnGeneratorService/incrementsAnExistingSequence').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.hn).to.equal("2601120005")
    })
  })

  it('HnGeneratorService: rejectsABranchWithoutACode', () => {
    cy.api('POST', '/__cypress/v2/core/HnGeneratorService/rejectsABranchWithoutACode').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('PatientService: registersAPatientAndAssignsAnHn', () => {
    cy.api('POST', '/__cypress/v2/core/PatientService/registersAPatientAndAssignsAnHn').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.patientHn).to.equal("2601120001")
      expect(body.patientFname).to.equal("สมชาย")
      expect(body.registeredBy).to.equal(1)
    })
  })

  it('PatientService: rejectsMissingBranch', () => {
    cy.api('POST', '/__cypress/v2/core/PatientService/rejectsMissingBranch').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('PatientService: rejectsMissingRequiredField', () => {
    cy.api('POST', '/__cypress/v2/core/PatientService/rejectsMissingRequiredField').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('RoleService: createsRole', () => {
    cy.api('POST', '/__cypress/v2/core/RoleService/createsRole').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.roleName).to.equal("MANAGER")
    })
  })

  it('RoleService: rejectsDuplicateRoleName', () => {
    cy.api('POST', '/__cypress/v2/core/RoleService/rejectsDuplicateRoleName').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('RoleService: grantPermissionIsIdempotent', () => {
    cy.api('POST', '/__cypress/v2/core/RoleService/grantPermissionIsIdempotent').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.rolePermissionsSaveCalls).to.equal(0)
    })
  })

  it('SystemSettingService: createsANewSetting', () => {
    cy.api('POST', '/__cypress/v2/core/SystemSettingService/createsANewSetting').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.settingKey).to.equal("TREATMENT_FEE_OVERFLOW_POLICY")
      expect(body.settingValue).to.equal("CAP_AT_COMMISSION")
    })
  })

  it('SystemSettingService: updatesAnExistingSettingInPlace', () => {
    cy.api('POST', '/__cypress/v2/core/SystemSettingService/updatesAnExistingSettingInPlace').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.sameSettingInstance).to.equal(true)
      expect(body.settingValue).to.equal("CAP_AT_COMMISSION")
    })
  })

  it('SystemSettingService: rejectsBlankValue', () => {
    cy.api('POST', '/__cypress/v2/core/SystemSettingService/rejectsBlankValue').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('StaffService: createsStaffAssignedToABranch', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/createsStaffAssignedToABranch').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.staffCode).to.equal("THP-001")
      expect(body.staffStatus).to.equal(true)
      expect(body.assignedBranchIsRequestedBranch).to.equal(true)
    })
  })

  it('StaffService: rejectsDuplicateStaffCode', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/rejectsDuplicateStaffCode').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
      expect(body.errorMessage).to.include("ซ้ำ")
    })
  })

  it('StaffService: rejectsMissingBranch', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/rejectsMissingBranch').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('StaffService: terminateSetsStatusAndPolicy', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/terminateSetsStatusAndPolicy').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.staffStatus).to.equal(false)
      expect(body.terminationDate).to.equal("2026-06-30")
      expect(body.commissionAfterTerminationPolicy).to.equal("CONTINUE_UNTIL_COMPLETE")
    })
  })

  it('StaffService: terminateStampsThePolicyLockTime', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/terminateStampsThePolicyLockTime').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.locked).to.equal(true)
    })
  })

  it('StaffService: rejectsChangingALockedTerminationPolicy', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/rejectsChangingALockedTerminationPolicy').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })

  it('StaffService: repeatingTheSameLockedPolicyKeepsTheOriginalLockTime', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/repeatingTheSameLockedPolicyKeepsTheOriginalLockTime').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.lockedAtUnchanged).to.equal(true)
    })
  })

  it('StaffService: terminateRequiresAPolicy', () => {
    cy.api('POST', '/__cypress/v2/core/StaffService/terminateRequiresAPolicy').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.error).to.equal('IllegalArgumentException')
    })
  })
})
