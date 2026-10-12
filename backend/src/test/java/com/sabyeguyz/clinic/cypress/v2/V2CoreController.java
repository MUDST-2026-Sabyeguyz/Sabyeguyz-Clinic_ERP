package com.sabyeguyz.clinic.cypress.v2;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Test-only bridge. Each request executes an isolated original service scenario. */
@RestController
@Profile("cypress")
@PreAuthorize("hasRole('MANAGER')")
public class V2CoreController {
    @PostMapping("/__cypress/v2/core/{group}/{scenario}")
    public Map<String, Object> run(@PathVariable String group, @PathVariable String scenario) {
        return switch (group + "/" + scenario) {
            case "AppUserService/registerHashesThePassword" -> new com.sabyeguyz.clinicv2.auth.AppUserServiceScenarios().registerHashesThePassword();
            case "AppUserService/rejectsShortPassword" -> new com.sabyeguyz.clinicv2.auth.AppUserServiceScenarios().rejectsShortPassword();
            case "AppUserService/loginRejectsWrongPassword" -> new com.sabyeguyz.clinicv2.auth.AppUserServiceScenarios().loginRejectsWrongPassword();
            case "AppUserService/loginRejectsDeactivatedUser" -> new com.sabyeguyz.clinicv2.auth.AppUserServiceScenarios().loginRejectsDeactivatedUser();
            case "AppUserService/grantBranchAccessIsIdempotent" -> new com.sabyeguyz.clinicv2.auth.AppUserServiceScenarios().grantBranchAccessIsIdempotent();
            case "AppUserService/assignRoleSavesWhenNotAlreadyAssigned" -> new com.sabyeguyz.clinicv2.auth.AppUserServiceScenarios().assignRoleSavesWhenNotAlreadyAssigned();
            case "BranchService/createsBranchWithValidTwoDigitCode" -> new com.sabyeguyz.clinicv2.branch.BranchServiceScenarios().createsBranchWithValidTwoDigitCode();
            case "BranchService/rejectsNonNumericBranchCode" -> new com.sabyeguyz.clinicv2.branch.BranchServiceScenarios().rejectsNonNumericBranchCode();
            case "BranchService/rejectsDuplicateBranchCode" -> new com.sabyeguyz.clinicv2.branch.BranchServiceScenarios().rejectsDuplicateBranchCode();
            case "BranchService/rejectsBlankAddress" -> new com.sabyeguyz.clinicv2.branch.BranchServiceScenarios().rejectsBlankAddress();
            case "BranchService/updateNeverTouchesBranchCode" -> new com.sabyeguyz.clinicv2.branch.BranchServiceScenarios().updateNeverTouchesBranchCode();
            case "BranchService/deactivateThrowsWhenBranchMissing" -> new com.sabyeguyz.clinicv2.branch.BranchServiceScenarios().deactivateThrowsWhenBranchMissing();
            case "CourseService/sellsACourseWithAProvisionalTierRate" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().sellsACourseWithAProvisionalTierRate();
            case "CourseService/appendsSequenceSuffixWhenMultipleCoursesShareAReceipt" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().appendsSequenceSuffixWhenMultipleCoursesShareAReceipt();
            case "CourseService/remainingVisitsIncludesBonusButCommissionRateIgnoresIt" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().remainingVisitsIncludesBonusButCommissionRateIgnoresIt();
            case "CourseService/rejectsACourseTemplateWithoutADefaultValidity" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().rejectsACourseTemplateWithoutADefaultValidity();
            case "CourseService/recordsThePurchaserTheSaleLineAndEntitledVisits" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().recordsThePurchaserTheSaleLineAndEntitledVisits();
            case "CourseService/rejectsASaleLineThatIsNotACoursePurchase" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().rejectsASaleLineThatIsNotACoursePurchase();
            case "CourseService/rejectsASaleLineThatAlreadyHasACourse" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().rejectsASaleLineThatAlreadyHasACourse();
            case "CourseService/rejectsAPurchaserThatDiffersFromTheReceiptPatient" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().rejectsAPurchaserThatDiffersFromTheReceiptPatient();
            case "CourseService/creditsTheSaleToTheSellerAtOneHundredPercentByDefault" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().creditsTheSaleToTheSellerAtOneHundredPercentByDefault();
            case "CourseService/splitsTheSaleCreditBetweenTwoStaff" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().splitsTheSaleCreditBetweenTwoStaff();
            case "CourseService/rejectsSaleSharesThatDoNotTotalOneHundred" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().rejectsSaleSharesThatDoNotTotalOneHundred();
            case "CourseService/rejectsTheSameStaffTwiceInTheSaleShares" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().rejectsTheSameStaffTwiceInTheSaleShares();
            case "CourseService/createsTheOwnerMemberAndEntitlementForThePurchaser" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().createsTheOwnerMemberAndEntitlementForThePurchaser();
            case "CourseService/recordsThePurchaseLedgerEntryAgainstTheSaleLine" -> new com.sabyeguyz.clinicv2.catalog.CourseServiceScenarios().recordsThePurchaseLedgerEntryAgainstTheSaleLine();
            case "ServiceCatalogService/createsASingleVisitService" -> new com.sabyeguyz.clinicv2.catalog.ServiceCatalogServiceScenarios().createsASingleVisitService();
            case "ServiceCatalogService/courseRequiresADefaultTotalVisits" -> new com.sabyeguyz.clinicv2.catalog.ServiceCatalogServiceScenarios().courseRequiresADefaultTotalVisits();
            case "ServiceCatalogService/rejectsNegativePrice" -> new com.sabyeguyz.clinicv2.catalog.ServiceCatalogServiceScenarios().rejectsNegativePrice();
            case "ServiceCatalogService/rejectsAnAssessmentService" -> new com.sabyeguyz.clinicv2.catalog.ServiceCatalogServiceScenarios().rejectsAnAssessmentService();
            case "MasterDataService/createsAType" -> new com.sabyeguyz.clinicv2.masterdata.MasterDataServiceScenarios().createsAType();
            case "MasterDataService/rejectsDuplicateTypeName" -> new com.sabyeguyz.clinicv2.masterdata.MasterDataServiceScenarios().rejectsDuplicateTypeName();
            case "MasterDataService/createsAValueUnderAType" -> new com.sabyeguyz.clinicv2.masterdata.MasterDataServiceScenarios().createsAValueUnderAType();
            case "MasterDataService/deactivateThrowsWhenValueMissing" -> new com.sabyeguyz.clinicv2.masterdata.MasterDataServiceScenarios().deactivateThrowsWhenValueMissing();
            case "HnGeneratorService/formatsYearBranchMonthAndRunningNumber" -> new com.sabyeguyz.clinicv2.patient.HnGeneratorServiceScenarios().formatsYearBranchMonthAndRunningNumber();
            case "HnGeneratorService/startsANewSequenceAtOne" -> new com.sabyeguyz.clinicv2.patient.HnGeneratorServiceScenarios().startsANewSequenceAtOne();
            case "HnGeneratorService/incrementsAnExistingSequence" -> new com.sabyeguyz.clinicv2.patient.HnGeneratorServiceScenarios().incrementsAnExistingSequence();
            case "HnGeneratorService/rejectsABranchWithoutACode" -> new com.sabyeguyz.clinicv2.patient.HnGeneratorServiceScenarios().rejectsABranchWithoutACode();
            case "PatientService/registersAPatientAndAssignsAnHn" -> new com.sabyeguyz.clinicv2.patient.PatientServiceScenarios().registersAPatientAndAssignsAnHn();
            case "PatientService/rejectsMissingBranch" -> new com.sabyeguyz.clinicv2.patient.PatientServiceScenarios().rejectsMissingBranch();
            case "PatientService/rejectsMissingRequiredField" -> new com.sabyeguyz.clinicv2.patient.PatientServiceScenarios().rejectsMissingRequiredField();
            case "RoleService/createsRole" -> new com.sabyeguyz.clinicv2.role.RoleServiceScenarios().createsRole();
            case "RoleService/rejectsDuplicateRoleName" -> new com.sabyeguyz.clinicv2.role.RoleServiceScenarios().rejectsDuplicateRoleName();
            case "RoleService/grantPermissionIsIdempotent" -> new com.sabyeguyz.clinicv2.role.RoleServiceScenarios().grantPermissionIsIdempotent();
            case "SystemSettingService/createsANewSetting" -> new com.sabyeguyz.clinicv2.setting.SystemSettingServiceScenarios().createsANewSetting();
            case "SystemSettingService/updatesAnExistingSettingInPlace" -> new com.sabyeguyz.clinicv2.setting.SystemSettingServiceScenarios().updatesAnExistingSettingInPlace();
            case "SystemSettingService/rejectsBlankValue" -> new com.sabyeguyz.clinicv2.setting.SystemSettingServiceScenarios().rejectsBlankValue();
            case "StaffService/createsStaffAssignedToABranch" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().createsStaffAssignedToABranch();
            case "StaffService/rejectsDuplicateStaffCode" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().rejectsDuplicateStaffCode();
            case "StaffService/rejectsMissingBranch" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().rejectsMissingBranch();
            case "StaffService/terminateSetsStatusAndPolicy" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().terminateSetsStatusAndPolicy();
            case "StaffService/terminateRequiresAPolicy" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().terminateRequiresAPolicy();
            case "StaffService/terminateStampsThePolicyLockTime" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().terminateStampsThePolicyLockTime();
            case "StaffService/rejectsChangingALockedTerminationPolicy" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().rejectsChangingALockedTerminationPolicy();
            case "StaffService/repeatingTheSameLockedPolicyKeepsTheOriginalLockTime" -> new com.sabyeguyz.clinicv2.staff.StaffServiceScenarios().repeatingTheSameLockedPolicyKeepsTheOriginalLockTime();
            default -> throw new IllegalArgumentException("Unknown core scenario: " + group + "/" + scenario);
        };
    }
}
