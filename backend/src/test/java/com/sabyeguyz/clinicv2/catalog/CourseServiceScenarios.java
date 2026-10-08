package com.sabyeguyz.clinicv2.catalog;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.commission.CommissionSchemeResolver;
import com.sabyeguyz.clinicv2.commission.CommissionTier;
import com.sabyeguyz.clinicv2.commission.CommissionTierResolver;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Numbers mirror the Commission_System_Requirement_Dev §4 worked example (10,000 THB course, 7% tier). */
public class CourseServiceScenarios {
    V2RepositoryStub<CourseRepository> coursesStub = new V2RepositoryStub<>(CourseRepository.class);
    CourseRepository courses = coursesStub.proxy();
    V2RepositoryStub<CourseLedgerRepository> ledgerStub = new V2RepositoryStub<>(CourseLedgerRepository.class);
    CourseLedgerRepository ledger = ledgerStub.proxy();
    CommissionScheme schemeResult;
    CommissionSchemeResolver schemeResolver = new CommissionSchemeResolver(null) {
        @Override public CommissionScheme resolveEffective(LocalDate date) { return schemeResult; }
    };
    CommissionTier tierResult;
    CommissionTierResolver tierResolver = new CommissionTierResolver(null) {
        @Override public CommissionTier resolve(CommissionScheme scheme, BigDecimal sales) { return tierResult; }
    };

    private CourseService service() {
        return new CourseService(courses, ledger, schemeResolver, tierResolver);
    }

    /** A CoursePurchase sale line for the receipt's patient, as SalesTransactionService records it before selling the Course. */
    private static SalesTransaction purchaseLine(Receipt receipt) {
        Patient patient = new Patient();
        receipt.setPatient(patient);
        SalesTransaction transaction = new SalesTransaction();
        transaction.setPatient(patient);
        transaction.setTransactionType(TransactionType.CoursePurchase);
        transaction.setAmount(new BigDecimal("10000.00"));
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
        receipt.setReceiptNo("RE-2206315");
        CommissionScheme scheme = new CommissionScheme();
        scheme.setSchemeVersion(1);
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));

        coursesStub.on("countByReceiptId", args -> 0L);
        coursesStub.on("sumCoursePriceBySellerAndSaleMonth", args -> BigDecimal.ZERO);
        schemeResult = scheme;
        tierResult = tier;

        Course course = service().sell(receipt, purchaseLine(receipt), courseTemplate(), new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("courseNo", course.getCourseNo());
        out.put("coursePrice", course.getCoursePrice());
        out.put("totalVisits", course.getTotalVisits());
        out.put("remainingVisits", course.getRemainingVisits());
        out.put("provisionalCommissionRate", course.getProvisionalCommissionRate());
        out.put("commissionStatus", course.getCommissionStatus());
        out.put("courseStatus", course.getCourseStatus());
        out.put("caseOwnerIsSeller", course.getCaseOwnerStaff() == course.getSellerStaff());
        out.put("expiryDate", course.getExpiryDate());
        return out;
    }

    public Map<String, Object> appendsSequenceSuffixWhenMultipleCoursesShareAReceipt() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-2206315");
        coursesStub.on("countByReceiptId", args -> 1L);
        coursesStub.on("sumCoursePriceBySellerAndSaleMonth", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();

        Course course = service().sell(receipt, purchaseLine(receipt), courseTemplate(), new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("courseNo", course.getCourseNo());
        out.put("courseSequence", course.getCourseSequence());
        return out;
    }

    public Map<String, Object> remainingVisitsIncludesBonusButCommissionRateIgnoresIt() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-3000001");
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));
        coursesStub.on("countByReceiptId", args -> 0L);
        coursesStub.on("sumCoursePriceBySellerAndSaleMonth", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = tier;

        Course course = service().sell(receipt, purchaseLine(receipt), template, new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("totalVisits", course.getTotalVisits());
        out.put("remainingVisits", course.getRemainingVisits());
        return out;
    }

    public Map<String, Object> rejectsACourseTemplateWithoutADefaultValidity() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultValidityDays(null);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-4000001");

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, purchaseLine(receipt), template, new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> recordsThePurchaserTheSaleLineAndEntitledVisits() {
        Map<String, Object> out = new LinkedHashMap<>();
        Service template = courseTemplate();
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-5000001");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("sumCoursePriceBySellerAndSaleMonth", args -> BigDecimal.ZERO);
        schemeResult = new CommissionScheme();
        tierResult = new CommissionTier();

        Course course = service().sell(receipt, line, template, new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        out.put("samePurchaser", course.getPurchaserPatient() == line.getPatient());
        out.put("sameSaleLine", course.getSaleTransaction() == line);
        out.put("entitledVisits", course.getEntitledVisits());
        return out;
    }

    public Map<String, Object> rejectsASaleLineThatIsNotACoursePurchase() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-5000002");
        SalesTransaction line = purchaseLine(receipt);
        line.setTransactionType(TransactionType.SingleVisit);

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, line, courseTemplate(), new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsASaleLineThatAlreadyHasACourse() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-5000003");
        SalesTransaction line = purchaseLine(receipt);
        coursesStub.on("existsBySaleTransactionId", args -> true);

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, line, courseTemplate(), new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsAPurchaserThatDiffersFromTheReceiptPatient() {
        Map<String, Object> out = new LinkedHashMap<>();
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-5000004");
        SalesTransaction line = purchaseLine(receipt);
        receipt.setPatient(new Patient());

        out.putAll(V2RepositoryStub.outcome(() -> { service().sell(receipt, line, courseTemplate(), new Staff(), null, null, null,
                LocalDate.of(2026, 8, 18), 1L); return null; }));
        return out;
    }
}
