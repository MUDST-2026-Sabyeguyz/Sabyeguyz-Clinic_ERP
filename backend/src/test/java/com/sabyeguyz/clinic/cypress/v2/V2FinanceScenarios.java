package com.sabyeguyz.clinic.cypress.v2;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.catalog.CourseService;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.commission.*;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.enums.*;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.sale.*;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Scenario setup and observations only; Cypress owns every assertion. */
@RestController
@Profile("cypress")
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/__cypress/v2/finance")
public class V2FinanceScenarios {
    @PostMapping("/{scenario}")
    public Map<String, Object> run(@PathVariable String scenario) {
        return V2RepositoryStub.outcome(() -> {
            if (scenario.startsWith("override-")) return override(scenario);
            if (scenario.startsWith("scheme-")) return scheme(scenario);
            if (scenario.startsWith("schemecrud-")) return schemeCrud(scenario);
            if (scenario.startsWith("tier-")) return tier(scenario);
            if (scenario.startsWith("closing-")) return closing(scenario);
            if (scenario.startsWith("fee-")) return fee(scenario);
            if (scenario.startsWith("payment-")) return payment(scenario);
            if (scenario.startsWith("receipt-")) return receipt(scenario);
            if (scenario.startsWith("sale-")) return sale(scenario);
            if (scenario.startsWith("discount-")) return discount(scenario);
            if (scenario.startsWith("preview-")) return preview(scenario);
            throw new IllegalArgumentException("Unknown finance scenario: " + scenario);
        });
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
    private static Course course() {
        Course c = new Course(); c.setCoursePrice(decimal("10000.00")); c.setTotalVisits(10);
        c.setGrossCommissionAllocatedTotal(BigDecimal.ZERO); return c;
    }
    private static Staff staff() {
        Staff s = new Staff();
        try { var f = Staff.class.getDeclaredField("id"); f.setAccessible(true); f.set(s, 1L); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return s;
    }
    private static CommissionScheme schemeVersion(int version, String from, String to) {
        CommissionScheme s = new CommissionScheme(); s.setSchemeVersion(version);
        s.setEffectiveFrom(LocalDate.parse(from)); s.setEffectiveTo(to == null ? null : LocalDate.parse(to)); return s;
    }
    private static CommissionTier tierValue(int order, String min, String max, String rate) {
        CommissionTier t = new CommissionTier(); t.setTierOrder(order); t.setMinimumMonthlySales(decimal(min));
        t.setMaximumMonthlySales(max == null ? null : decimal(max)); t.setCommissionRate(decimal(rate)); return t;
    }
    private static List<CommissionTier> tiers() {
        return List.of(tierValue(1,"0","59999","0.05"), tierValue(2,"60000","69999","0.06"),
                tierValue(3,"70000","79999","0.07"), tierValue(4,"80000","89999","0.08"), tierValue(5,"90000",null,"0.10"));
    }
    private static CommissionSchemeResolver schemeResolver() {
        return new CommissionSchemeResolver(new V2RepositoryStub<>(CommissionSchemeRepository.class)
                .on("findByOrderByEffectiveFromDesc", a -> List.of(schemeVersion(1,"2026-01-01",null))).proxy());
    }
    private static CommissionTierResolver tierResolver() {
        return new CommissionTierResolver(new V2RepositoryStub<>(CommissionTierRepository.class)
                .on("findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder", a -> tiers()).proxy());
    }
    private Object override(String scenario) {
        var audits = new V2RepositoryStub<>(CommissionRateOverrideAuditRepository.class);
        var courses = new V2RepositoryStub<>(CourseRepository.class);
        Course c = course(); c.setGrossCommissionAllocatedTotal(decimal("210.00"));
        if (!scenario.equals("override-unlocked")) c.setLockedCommissionRate(decimal("0.07"));
        var audit = new CommissionRateOverrideService(audits.proxy(), courses.proxy()).override(c, decimal("0.10"),
                new Permission(), scenario.equals("override-reason") ? " " : "แก้ตามคำสั่งผู้บริหาร", 1L);
        return Map.of("oldRate",audit.getOldRate(),"newRate",audit.getNewRate(),"rate",c.getLockedCommissionRate(),
                "pool",c.getTotalCourseCommissionPool(),"outstanding",c.getCommissionOutstanding());
    }
    private Object scheme(String scenario) {
        var v1 = schemeVersion(1,"2026-01-01","2027-01-01"); var v2 = schemeVersion(2,"2027-01-01",null);
        var repository = new V2RepositoryStub<>(CommissionSchemeRepository.class).on("findByOrderByEffectiveFromDesc",
                a -> scenario.equals("scheme-missing") ? List.of() : scenario.equals("scheme-newest") ? List.of(v2,v1) : List.of(v1));
        var resolved = new CommissionSchemeResolver(repository.proxy()).resolveEffective(
                LocalDate.parse(scenario.equals("scheme-newest") ? "2027-06-01" : "2026-08-18"));
        return Map.of("sameV1",resolved == v1,"sameV2",resolved == v2);
    }
    /** Coverage for CommissionSchemeService (SS2 tier validation, SS24 Tier Versioning) - no prior Scenarios existed for this. */
    private Object schemeCrud(String scenario) {
        var schemesStub = new V2RepositoryStub<>(CommissionSchemeRepository.class);
        var tiersStub = new V2RepositoryStub<>(CommissionTierRepository.class);
        schemesStub.on("save", a -> a[0]);
        var savedTiers = new ArrayList<CommissionTier>();
        tiersStub.on("save", a -> { savedTiers.add((CommissionTier) a[0]); return a[0]; });
        var service = new CommissionSchemeService(schemesStub.proxy(), tiersStub.proxy());

        List<CommissionSchemeService.TierInput> validTiers = List.of(
                new CommissionSchemeService.TierInput(decimal("0"), decimal("59999"), decimal("0.05"), 1),
                new CommissionSchemeService.TierInput(decimal("60000"), null, decimal("0.10"), 2));

        if (scenario.equals("schemecrud-create")) {
            schemesStub.on("findByOrderByEffectiveFromDesc", a -> List.of());
            var created = service.create("Default", LocalDate.parse("2026-01-01"), null, new Permission(), validTiers, 1L);
            return Map.of("version", created.getSchemeVersion(), "tiersSaved", savedTiers.size(),
                    "firstRate", savedTiers.get(0).getCommissionRate());
        }
        if (scenario.equals("schemecrud-next-version")) {
            var existing = schemeVersion(1, "2026-01-01", null);
            schemesStub.on("findByOrderByEffectiveFromDesc", a -> List.of(existing));
            var created = service.create("V2", LocalDate.parse("2027-01-01"), null, new Permission(), validTiers, 1L);
            return Map.of("version", created.getSchemeVersion());
        }
        if (scenario.equals("schemecrud-overlap")) {
            // Case 8 from the requirement doc: 0-59,999=5% then 50,000-79,999=7% overlaps.
            List<CommissionSchemeService.TierInput> overlapping = List.of(
                    new CommissionSchemeService.TierInput(decimal("0"), decimal("59999"), decimal("0.05"), 1),
                    new CommissionSchemeService.TierInput(decimal("50000"), decimal("79999"), decimal("0.07"), 2));
            return V2RepositoryStub.outcome(() ->
                    service.create("Overlap", LocalDate.parse("2026-01-01"), null, new Permission(), overlapping, 1L));
        }
        if (scenario.equals("schemecrud-gap")) {
            List<CommissionSchemeService.TierInput> gapped = List.of(
                    new CommissionSchemeService.TierInput(decimal("0"), decimal("49999"), decimal("0.05"), 1),
                    new CommissionSchemeService.TierInput(decimal("60000"), null, decimal("0.10"), 2));
            return V2RepositoryStub.outcome(() ->
                    service.create("Gap", LocalDate.parse("2026-01-01"), null, new Permission(), gapped, 1L));
        }
        if (scenario.equals("schemecrud-update-keeps-tiers")) {
            var existing = schemeVersion(1, "2026-01-01", null);
            schemesStub.on("findById", a -> Optional.of(existing));
            service.update(existing.getId(), "Renamed", null, null, 9L);
            return Map.of("name", existing.getSchemeName(), "tiersSaveCallsDuringUpdate", tiersStub.calls("save"));
        }
        throw new IllegalArgumentException("Unknown schemecrud scenario: " + scenario);
    }
    private Object tier(String scenario) {
        var repository = new V2RepositoryStub<>(CommissionTierRepository.class)
                .on("findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder", a -> scenario.equals("tier-missing") ? List.of() : tiers());
        return new CommissionTierResolver(repository.proxy()).resolve(new CommissionScheme(),
                decimal(scenario.equals("tier-top") ? "500000" : scenario.equals("tier-missing") ? "1000" : "75000")).getCommissionRate();
    }
    private Object closing(String scenario) {
        var closings = new V2RepositoryStub<>(MonthlyCommissionClosingRepository.class);
        var existing = new MonthlyCommissionClosing(); existing.setClosingStatus(true);
        closings.on("findByClosingMonthAndStaffId", a -> scenario.equals("closing-duplicate") ? Optional.of(existing) : Optional.empty());
        Course c = course();
        var courses = new V2RepositoryStub<>(CourseRepository.class)
                .on("sumCoursePriceBySellerAndSaleMonth", a -> decimal("75000"))
                .on("findBySellerStaffIdAndSaleMonth", a -> List.of(c));
        var result = new MonthlyCommissionClosingService(closings.proxy(),courses.proxy(),schemeResolver(),tierResolver())
                .closeMonth(scenario.equals("closing-malformed") ? "August-2026" : "2026-08",staff(),null,9L);
        return Map.of("rate",result.getLockedCommissionRate(),"status",result.isClosingStatus(),
                "courseStatus",c.getCommissionStatus(),"courseRate",c.getLockedCommissionRate(),
                "pool",c.getTotalCourseCommissionPool(),"perVisit",c.getCommissionAllocationPerVisit());
    }
    private Object fee(String scenario) {
        var repository = new V2RepositoryStub<>(TreatmentFeeRuleRepository.class);
        var rule = new TreatmentFeeRule(); rule.setFeeType(FeeType.Fixed); rule.setFeeValue(decimal("45"));
        if (scenario.equals("fee-specific")) {
            var s = staff(); rule.setStaff(s);
            var generic = new TreatmentFeeRule(); generic.setFeeType(FeeType.Fixed); generic.setFeeValue(decimal("40"));
            repository.on("findActiveOn", a -> List.of(generic,rule));
            var resolved = new TreatmentFeeRuleResolver(repository.proxy()).resolve(s,LocalDate.of(2026,8,20));
            return Map.of("present",resolved.isPresent(),"fee",resolved.map(TreatmentFeeRule::getFeeValue).orElse(BigDecimal.ZERO));
        }
        if (scenario.equals("fee-percentage")) {
            rule.setFeeType(FeeType.Percentage); rule.setFeeValue(decimal("0.03")); rule.setPercentageBase(CalculationBase.CourseValuePerVisit);
        }
        return new TreatmentFeeRuleResolver(repository.proxy()).calculateFee(rule,course());
    }
    private Object payment(String scenario) {
        var repository = new V2RepositoryStub<>(PaymentTransactionRepository.class);
        var transaction = new SalesTransaction(); transaction.setTransactionStatus(!scenario.equals("payment-void"));
        var result = new PaymentTransactionService(repository.proxy()).record(new Patient(),new Branch(),transaction,
                scenario.equals("payment-zero") ? BigDecimal.ZERO : decimal("1200.00"),new PaymentMethod(),1L);
        return Map.of("amount",result.getAmount(),"status",result.isPaymentStatus());
    }
    private Object receipt(String scenario) {
        var repository = new V2RepositoryStub<>(ReceiptRepository.class).on("findByReceiptNo",
                a -> scenario.equals("receipt-duplicate") ? Optional.of(new Receipt()) : Optional.empty());
        var result = new ReceiptService(repository.proxy()).create("RE-2600001",new Patient(),new Branch(),LocalDate.of(2026,8,18),1L);
        return Map.of("number",result.getReceiptNo(),"date",result.getSaleDate());
    }
    private Object sale(String scenario) {
        var transactions = new V2RepositoryStub<>(SalesTransactionRepository.class);
        var courses = new V2RepositoryStub<>(CourseRepository.class)
                .on("sumCoursePriceBySellerAndSaleMonth", a -> BigDecimal.ZERO);
        var ledger = new V2RepositoryStub<>(CourseLedgerRepository.class);
        List<String> saveOrder = new ArrayList<>();
        courses.on("save", a -> { saveOrder.add("course"); return a[0]; });
        transactions.on("save", a -> { saveOrder.add("transaction"); return a[0]; });
        var service = new SalesTransactionService(transactions.proxy(),new CourseService(courses.proxy(),ledger.proxy(),schemeResolver(),tierResolver()));
        Service template = new Service(); template.setServiceType(ServiceType.SingleVisit); template.setServicePrice(decimal("1200.00"));
        if (scenario.equals("sale-void")) {
            var transaction = new SalesTransaction(); transaction.setTransactionStatus(false);
            transactions.on("findById", a -> Optional.of(transaction)); return service.voidTransaction(1L,1L);
        }
        if (scenario.equals("sale-course")) {
            template.setDefaultTotalVisits(10); template.setDefaultBonusVisits(0); template.setDefaultValidityDays(365);
            var patient = new Patient();
            var receipt = new Receipt(); receipt.setReceiptNo("RE-2600001"); receipt.setPatient(patient);
            var transaction = service.recordCoursePurchase(patient,new Branch(),receipt,template,staff(),null,
                    decimal("10000.00"),null,null,Discount.none(),LocalDate.of(2026,8,18),1L);
            return Map.of("sameCourse",transaction.getCourse() == courses.arguments("save").get(0)[0],
                    "amount",transaction.getAmount(),"type",transaction.getTransactionType(),"saveOrder",saveOrder);
        }
        if (scenario.equals("sale-visit-discount")) {
            var discounted = service.recordVisitSale(new Patient(),new Branch(),template,TransactionType.SingleVisit,
                    new Staff(),new Staff(),1,null,null,Discount.of("PERCENT",decimal("10")),1L);
            return Map.of("unitPrice",discounted.getUnitPrice(),"amount",discounted.getAmount());
        }
        if (scenario.startsWith("sale-course-discount-")) {
            template.setServiceType(ServiceType.Course); template.setServicePrice(decimal("2000.00"));
            template.setDefaultTotalVisits(10); template.setDefaultBonusVisits(0); template.setDefaultValidityDays(365);
            var patient = new Patient();
            var receipt = new Receipt(); receipt.setReceiptNo("RE-2600002"); receipt.setPatient(patient);
            var discount = scenario.equals("sale-course-discount-percent")
                    ? Discount.of("PERCENT",decimal("25")) : Discount.of("BAHT",decimal("500"));
            var purchase = service.recordCoursePurchase(patient,new Branch(),receipt,template,staff(),null,null,null,null,
                    discount,LocalDate.of(2026,8,18),1L);
            return Map.of("unitPrice",purchase.getUnitPrice(),"amount",purchase.getAmount(),
                    "coursePrice",purchase.getCourse().getCoursePrice());
        }
        var transaction = service.recordVisitSale(new Patient(),new Branch(),template,
                scenario.equals("sale-wrong-type") ? TransactionType.CoursePurchase
                        : scenario.equals("sale-assessment-rejected") ? TransactionType.Assessment : TransactionType.SingleVisit,
                new Staff(),new Staff(),1,null,null,Discount.none(),1L);
        return Map.of("amount",transaction.getAmount(),"status",transaction.isTransactionStatus());
    }

    private Object discount(String scenario) {
        BigDecimal price = decimal("2000.00");
        return switch (scenario) {
            case "discount-percent" -> Discount.of("PERCENT", decimal("25")).apply(price);
            case "discount-amount" -> Discount.of("BAHT", decimal("500")).apply(price);
            case "discount-none" -> Discount.none().apply(price);
            case "discount-percent-over" -> Discount.of("PERCENT", decimal("101")).apply(price);
            case "discount-amount-over" -> Discount.of("BAHT", decimal("2000.01")).apply(price);
            case "discount-unknown-type" -> Discount.of("COUPON", decimal("5"));
            case "discount-value-without-type" -> Discount.of(null, decimal("5"));
            default -> throw new IllegalArgumentException("Unknown discount scenario: " + scenario);
        };
    }
    private Object preview(String scenario) {
        var courses = new V2RepositoryStub<>(CourseRepository.class).on("sumCoursePriceBySellerAndSaleMonth",
                a -> scenario.equals("preview-running-total") ? decimal("60000") : BigDecimal.ZERO);
        var service = new CommissionPreviewService(courses.proxy(), schemeResolver(), tierResolver());
        Service template = new Service(); template.setServiceType(ServiceType.Course);
        template.setServicePrice(decimal("2000.00")); template.setDefaultTotalVisits(10);
        if (scenario.equals("preview-invalid-template")) {
            template.setServiceType(ServiceType.SingleVisit);
            return service.previewCourse(template, staff(), null, Discount.none(), LocalDate.of(2026,8,18));
        }
        var result = service.previewCourse(template, staff(), null, Discount.of("BAHT", decimal("500")), LocalDate.of(2026,8,18));
        return Map.of("net",result.netPrice(),"rate",result.commissionRate(),"pool",result.commissionPool(),
                "perVisit",result.commissionPerVisit(),"monthlySales",result.monthlySalesToDate());
    }
}
