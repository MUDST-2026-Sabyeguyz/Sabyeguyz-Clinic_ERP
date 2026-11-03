package com.sabyeguyz.clinicv2.catalog;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.commission.CommissionSchemeResolver;
import com.sabyeguyz.clinicv2.commission.CommissionTier;
import com.sabyeguyz.clinicv2.commission.CommissionTierResolver;
import com.sabyeguyz.clinicv2.commission.MonthlyTierSalesService;
import com.sabyeguyz.clinicv2.commission.BranchMonthlyClosingService;
import com.sabyeguyz.clinicv2.commission.BranchMonthlyClosingRepository;
import com.sabyeguyz.clinicv2.commission.MonthlyCommissionClosingRepository;
import com.sabyeguyz.clinicv2.course.CourseEntitlement;
import com.sabyeguyz.clinicv2.course.CourseEntitlementRepository;
import com.sabyeguyz.clinicv2.course.CourseLedger;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.course.CourseSharedMember;
import com.sabyeguyz.clinicv2.course.CourseSharedMemberRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.branch.Branch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Numbers mirror the Commission_System_Requirement_Dev §4 worked example (10,000 THB course, 7% tier). */
public class CourseServiceScenarios {
    V2RepositoryStub<CourseRepository> coursesStub = new V2RepositoryStub<>(CourseRepository.class);
    CourseRepository courses = coursesStub.proxy();
    V2RepositoryStub<CourseLedgerRepository> ledgerStub = new V2RepositoryStub<>(CourseLedgerRepository.class);
    CourseLedgerRepository ledger = ledgerStub.proxy();
    V2RepositoryStub<CourseSharedMemberRepository> sharedMembersStub = new V2RepositoryStub<>(CourseSharedMemberRepository.class);
    V2RepositoryStub<CourseEntitlementRepository> entitlementsStub = new V2RepositoryStub<>(CourseEntitlementRepository.class);
    BigDecimal salesSeenByTier;
    CommissionScheme schemeResult;
    CommissionSchemeResolver schemeResolver = new CommissionSchemeResolver(null) {
        @Override public CommissionScheme resolveEffective(LocalDate date) { return schemeResult; }
    };
    CommissionTier tierResult;
    CommissionTierResolver tierResolver = new CommissionTierResolver(null) {
        @Override public CommissionTier resolve(CommissionScheme scheme, BigDecimal sales) {
            salesSeenByTier = sales;
            return tierResult;
        }
    };

    private CourseService service() {
        var branchClosingService = new BranchMonthlyClosingService(
                new V2RepositoryStub<>(BranchMonthlyClosingRepository.class).proxy(),
                new V2RepositoryStub<>(MonthlyCommissionClosingRepository.class).proxy(),
                courses);

        return new CourseService(
                courses, ledger, sharedMembersStub.proxy(), entitlementsStub.proxy(), schemeResolver, tierResolver,
                new MonthlyTierSalesService(courses),
                branchClosingService);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Staff staffFixture() {
        Staff person = new Staff();
        setId(person, 1L);
        return person;
    }

    /** A CoursePurchase sale line for the receipt's patient, as SalesTransactionService records it before selling the Course. */
    private static SalesTransaction purchaseLine(Receipt receipt) {
        Patient patient = new Patient();
        receipt.setPatient(patient);
        SalesTransaction transaction = new SalesTransaction();
        Branch branch = new Branch();
        setId(branch, 1L);
        transaction.setBranch(branch);
        transaction.setPatient(patient);
        transaction.setReceipt(receipt);
        transaction.setTransactionType(TransactionType.CoursePurchase);
        transaction.setAmount(new BigDecimal("10000.00"));
        transaction.setCommissionableAmount(new BigDecimal("10000.00"));
        return transaction;
    }

    private static Service courseTemplate() {
        Service template = new Service();
        template.setServiceType(ServiceType.Course);
        template.setServiceName("Routine Pain 10 Visits");
        template.setServicePrice(new BigDecimal("10000.00"));
        template.setDefaultTotalVisits(10);
        template.setDefaultBonusVisits(0);
        template.setDefaultValidityDays(90);
        return template;
    }

    public Map<String, Object> sellsACourseWithAProvisionalTierRate() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE22063150");
        CommissionScheme scheme = new CommissionScheme();
        scheme.setSchemeVersion(1);
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));

        coursesStub.on("countByReceiptId", args -> 0L);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = scheme;
        tierResult = tier;

        Course course = service().sell(receipt, purchaseLine(receipt), courseTemplate(), staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("courseNo", course.getCourseNo());
        out.put("coursePrice", course.getCoursePrice());
        out.put("totalVisits", course.getTotalVisits());
        out.put("remainingVisits", course.getRemainingVisits());
        out.put("provisionalCommissionRate", course.getProvisionalCommissionRate());
        out.put("commissionStatus", course.getCommissionStatus());
        out.put("courseStatus", course.getCourseStatus());
        out.put("caseOwnerIsSeller", course.getOwnerStaff() == course.getSellerStaff());
        out.put("expiryDate", course.getExpiryDate());
        return out;
    }

    public Map<String, Object> appendsSequenceSuffixWhenMultipleCoursesShareAReceipt() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE22063150");
        coursesStub.on("countByReceiptId", args -> 1L);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();

        Course course = service().sell(receipt, purchaseLine(receipt), courseTemplate(), staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("courseNo", course.getCourseNo());
        out.put("courseSequence", course.getCourseSequence());
        return out;
    }

    public Map<String, Object> remainingVisitsIncludesBonusButCommissionRateIgnoresIt() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultTotalVisits(13);
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE30000010");
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));
        coursesStub.on("countByReceiptId", args -> 0L);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = tier;

        Course course = service().sell(receipt, purchaseLine(receipt), template, staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("totalVisits", course.getTotalVisits());
        out.put("remainingVisits", course.getRemainingVisits());
        out.put("bonusVisits", course.getBonusVisits());
        return out;
    }

    public Map<String, Object> rejectsACourseTemplateWithoutADefaultValidity() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultValidityDays(null);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE40000010");

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, purchaseLine(receipt), template, staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> recordsThePurchaserTheSaleLineAndEntitledVisits() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultTotalVisits(13);
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE50000010");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();

        Course course = service().sell(receipt, line, template, staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("samePurchaser", course.getPurchaserPatient() == line.getPatient());
        out.put("sameSaleLine", course.getSaleTransaction() == line);
        out.put("entitledVisits", course.getTotalVisits());
        return out;
    }

    public Map<String, Object> rejectsASaleLineThatIsNotACoursePurchase() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE50000020");
        SalesTransaction line = purchaseLine(receipt);
        line.setTransactionType(TransactionType.SingleVisit);

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, line, courseTemplate(), staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsASaleLineThatAlreadyHasACourse() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE50000030");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("existsBySaleTransactionId", args -> true);

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, line, courseTemplate(), staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsAPurchaserThatDiffersFromTheReceiptPatient() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE50000040");
        SalesTransaction line = purchaseLine(receipt);
        receipt.setPatient(new Patient());

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, line, courseTemplate(), staffFixture(), null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> creditsTheWholeSaleToTheSeller() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE60000010");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();
        Staff seller = staffFixture();
        Course course = service().sell(receipt, line, courseTemplate(), seller, null, null, LocalDate.of(2026, 8, 18), 1L);
        out.put("ownerIsSeller", course.getOwnerStaff() == seller && course.getSellerStaff() == seller);
        out.put("salesSeenByTier", salesSeenByTier);
        return out;
    }

    public Map<String, Object> createsTheOwnerMemberAndEntitlementForThePurchaser() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultTotalVisits(13);
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE60000050");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();

        service().sell(receipt, line, template, staffFixture(), null, null, LocalDate.of(2026, 8, 18), 1L);

        CourseSharedMember member = (CourseSharedMember) sharedMembersStub.arguments("save").get(0)[0];
        CourseEntitlement entitlement = (CourseEntitlement) entitlementsStub.arguments("save").get(0)[0];
        out.put("memberRole", member.getMemberRole());
        out.put("memberIsPurchaser", member.getPatient() == line.getPatient());
        out.put("allocatedVisits", entitlement.getAllocatedVisits());
        out.put("usedVisits", entitlement.getUsedVisits());
        out.put("remainingVisits", entitlement.getRemainingVisits());
        return out;
    }

    public Map<String, Object> recordsThePurchaseLedgerEntryAgainstTheSaleLine() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultTotalVisits(13);
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE60000060");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("sumCreditedSalesByStaffAndSaleMonthAndBranchId", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();

        service().sell(receipt, line, template, staffFixture(), null, null, LocalDate.of(2026, 8, 18), 1L);

        CourseLedger entry = (CourseLedger) ledgerStub.arguments("save").get(0)[0];
        out.put("entryType", entry.getEntryType());
        out.put("qtyChange", entry.getQtyChange());
        out.put("balanceAfter", entry.getBalanceAfter());
        out.put("sourceIsTheSaleLine", entry.getSalesTransaction() == line);
        out.put("hasOtherSource", entry.getTransfer() != null || entry.getVisitUsage() != null || entry.getReversesLedger() != null);
        return out;
    }
}
