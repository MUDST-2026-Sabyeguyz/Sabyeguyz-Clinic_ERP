package com.sabyeguyz.clinic.cypress.v2;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.catalog.CourseService;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.commission.*;
import com.sabyeguyz.clinicv2.course.CourseEntitlementRepository;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.course.CourseSaleStaffShareRepository;
import com.sabyeguyz.clinicv2.course.CourseSharedMemberRepository;
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
            if (scenario.startsWith("cancel-")) return cancellation(scenario);
            if (scenario.startsWith("refund-")) return refund(scenario);
            if (scenario.startsWith("revenue-")) return revenue(scenario);
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
                .on("sumCreditedSalesByStaffAndSaleMonth", a -> decimal("75000"))
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
        var receipt = new Receipt(); receipt.setReceiptStatus(scenario.equals("payment-void") ? ReceiptStatus.Voided : ReceiptStatus.Issued);
        var receiptRepository = new V2RepositoryStub<>(ReceiptRepository.class).on("findByIdForUpdate", a -> Optional.of(receipt));
        var method = new PaymentMethod(); method.setMethodName("Cash");
        var result = new PaymentTransactionService(repository.proxy(), receiptRepository.proxy()).record(new Patient(),new Branch(),receipt,
                scenario.equals("payment-zero") ? BigDecimal.ZERO : decimal("1200.00"),method,1L);
        return Map.of("amount",result.getAmount(),"status",result.isPaymentStatus());
    }
    private Object receipt(String scenario) {
        var repository = new V2RepositoryStub<>(ReceiptRepository.class).on("findByReceiptNoAndBranchId",
                a -> scenario.equals("receipt-duplicate") ? Optional.of(new Receipt()) : Optional.empty());
        var service = new ReceiptService(repository.proxy());
        if (scenario.equals("receipt-next-no")) {
            repository.on("findMaxReceiptSequence", a -> 41);
            return service.nextReceiptNo();
        }
        if (scenario.equals("receipt-bad-format")) {
            return service.create("RE2600001",new Patient(),new Branch(),LocalDate.of(2026,8,18),1L);
        }
        if (scenario.startsWith("receipt-totals")) return receiptTotals(scenario, service);
        var result = service.create("RE26000010",new Patient(),new Branch(),LocalDate.of(2026,8,18),1L);
        return Map.of("number",result.getReceiptNo(),"date",result.getSaleDate());
    }
    private Object receiptTotals(String scenario, ReceiptService service) {
        boolean rounding = scenario.equals("receipt-totals-rounding");
        List<SalesTransaction> lines = new ArrayList<>();
        for (String amount : rounding ? new String[]{"100.00","100.00","100.00"} : new String[]{"1500.00","500.00"}) {
            var line = new SalesTransaction(); line.setAmount(decimal(amount)); line.setCommissionableAmount(decimal(amount));
            lines.add(line);
        }
        if (scenario.equals("receipt-totals-no-lines")) lines.clear();
        var receipt = new Receipt();
        service.finalizeTotals(receipt,lines,rounding ? Discount.of("FIXED",decimal("100")) : Discount.of("BAHT",decimal("200")),1L);
        return Map.of("subtotal",receipt.getSubtotalBeforeDiscount(),"billDiscount",receipt.getBillDiscountAmount(),
                "taxable",receipt.getTaxableAmount(),"tax",receipt.getTaxAmount(),"net",receipt.getNetAmount(),
                "billType",receipt.getBillDiscountType(),
                "allocated",lines.stream().map(SalesTransaction::getAllocatedBillDiscountAmount).toList(),
                "commissionable",lines.stream().map(SalesTransaction::getCommissionableAmount).toList());
    }
    private Object sale(String scenario) {
        var transactions = new V2RepositoryStub<>(SalesTransactionRepository.class);
        var courses = new V2RepositoryStub<>(CourseRepository.class)
                .on("sumCreditedSalesByStaffAndSaleMonth", a -> BigDecimal.ZERO);
        var ledger = new V2RepositoryStub<>(CourseLedgerRepository.class);
        List<String> saveOrder = new ArrayList<>();
        courses.on("save", a -> { saveOrder.add("course"); return a[0]; });
        transactions.on("save", a -> { saveOrder.add("transaction"); return a[0]; });
        var service = new SalesTransactionService(transactions.proxy(),new CourseService(courses.proxy(),ledger.proxy(),
                new V2RepositoryStub<>(CourseSaleStaffShareRepository.class).proxy(),
                new V2RepositoryStub<>(CourseSharedMemberRepository.class).proxy(),
                new V2RepositoryStub<>(CourseEntitlementRepository.class).proxy(),schemeResolver(),tierResolver()));
        Service template = new Service(); template.setServiceType(ServiceType.SingleVisit); template.setServicePrice(decimal("1200.00"));
        if (scenario.equals("sale-void")) {
            var transaction = new SalesTransaction(); transaction.setTransactionStatus(false);
            transactions.on("findById", a -> Optional.of(transaction)); return service.voidTransaction(1L,1L);
        }
        if (scenario.equals("sale-course")) {
            template.setDefaultTotalVisits(10); template.setDefaultBonusVisits(0); template.setDefaultValidityDays(365);
            var patient = new Patient();
            var receipt = new Receipt(); receipt.setReceiptNo("RE26000010"); receipt.setPatient(patient);
            var purchase = service.recordCoursePurchase(receipt,patient,new Branch(),template,staff(),null,
                    decimal("10000.00"),null,null,Discount.none(),null,LocalDate.of(2026,8,18),1L);
            var transaction = purchase.transaction();
            return Map.of("sameCourse",purchase.course() == courses.arguments("save").get(0)[0],
                    "sameLine",purchase.course().getSaleTransaction() == transaction,
                    "amount",transaction.getAmount(),"type",transaction.getTransactionType(),"saveOrder",saveOrder);
        }
        if (scenario.equals("sale-visit-discount")) {
            var discounted = service.recordVisitSale(new Receipt(),new Patient(),new Branch(),template,TransactionType.SingleVisit,
                    new Staff(),new Staff(),1,null,null,Discount.of("PERCENT",decimal("10")),1L);
            return Map.of("unitPrice",discounted.getUnitPrice(),"amount",discounted.getAmount(),
                    "lineDiscount",discounted.getLineDiscountAmount(),"lineDiscountType",discounted.getLineDiscountType(),
                    "commissionable",discounted.getCommissionableAmount(),"lineNo",discounted.getLineNo());
        }
        if (scenario.startsWith("sale-course-discount-")) {
            template.setServiceType(ServiceType.Course); template.setServicePrice(decimal("2000.00"));
            template.setDefaultTotalVisits(10); template.setDefaultBonusVisits(0); template.setDefaultValidityDays(365);
            var patient = new Patient();
            var receipt = new Receipt(); receipt.setReceiptNo("RE26000020"); receipt.setPatient(patient);
            var discount = scenario.equals("sale-course-discount-percent")
                    ? Discount.of("PERCENT",decimal("25")) : Discount.of("BAHT",decimal("500"));
            var purchase = service.recordCoursePurchase(receipt,patient,new Branch(),template,staff(),null,null,null,null,
                    discount,null,LocalDate.of(2026,8,18),1L);
            return Map.of("unitPrice",purchase.transaction().getUnitPrice(),"amount",purchase.transaction().getAmount(),
                    "commissionable",purchase.transaction().getCommissionableAmount(),
                    "coursePrice",purchase.course().getCoursePrice());
        }
        var transaction = service.recordVisitSale(new Receipt(),new Patient(),new Branch(),template,
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
        var courses = new V2RepositoryStub<>(CourseRepository.class).on("sumCreditedSalesByStaffAndSaleMonth",
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

    private static PaymentTransaction payment(String amount, boolean active) {
        var payment = new PaymentTransaction(); payment.setAmount(decimal(amount)); payment.setPaymentStatus(active);
        payment.setReceipt(new Receipt());
        return payment;
    }
    private static SalesTransaction line(TransactionType type, String amount, String allocatedBill) {
        var line = new SalesTransaction(); line.setTransactionType(type); line.setAmount(decimal(amount));
        line.setAllocatedBillDiscountAmount(decimal(allocatedBill)); line.setTransactionStatus(true);
        return line;
    }
    private Object cancellation(String scenario) {
        var cancellations = new V2RepositoryStub<>(TransactionCancellationRepository.class);
        var salesRepo = new V2RepositoryStub<>(SalesTransactionRepository.class);
        var paymentRepo = new V2RepositoryStub<>(PaymentTransactionRepository.class);
        var receiptRepo = new V2RepositoryStub<>(ReceiptRepository.class);
        var service = new TransactionCancellationService(cancellations.proxy(), salesRepo.proxy(),
                new SalesTransactionService(salesRepo.proxy(), null), paymentRepo.proxy(),
                new PaymentTransactionService(paymentRepo.proxy(), receiptRepo.proxy()), new ReceiptService(receiptRepo.proxy()));
        var receipt = new Receipt(); receipt.setReceiptStatus(ReceiptStatus.Issued);
        var sale = line(TransactionType.CoursePurchase, "1500.00", "0.00"); sale.setReceipt(receipt);
        sale.setTransactionStatus(!scenario.equals("cancel-already-void-sale"));
        receiptRepo.on("findByIdForUpdate", a -> Optional.of(receipt));
        var paid = payment("1500.00", true);
        paid.setReceipt(receipt);
        var other = line(TransactionType.SingleVisit, "500.00", "0.00");
        List<SalesTransaction> lines = scenario.equals("cancel-void-one-of-two-lines") ? List.of(sale, other) : List.of(sale);
        salesRepo.on("findById", a -> Optional.of(sale)).on("findByReceiptId", a -> lines);
        paymentRepo.on("findByReceiptIdOrderByIdAsc", a -> List.of(paid)).on("findById", a -> Optional.of(paid));
        var permission = scenario.equals("cancel-requires-permission") ? null : new Permission();
        String reason = scenario.equals("cancel-requires-reason") ? "  " : "ลูกค้าขอยกเลิก";
        if (scenario.equals("cancel-void-payment")) {
            var result = service.voidPayment(paid, permission, reason, 9L);
            return Map.of("paymentStatus", paid.isPaymentStatus(), "type", result.getCancellationType(),
                    "targetsPayment", result.getPayment() == paid, "reason", result.getReason());
        }
        var result = service.voidSale(sale, permission, reason, 9L);
        List<TransactionCancellation> saved = cancellations.arguments("save").stream()
                .map(a -> (TransactionCancellation) a[0]).toList();
        return Map.of("saleStatus", sale.isTransactionStatus(), "paymentStatus", paid.isPaymentStatus(),
                "receiptStatus", receipt.getReceiptStatus(), "cancellations", saved.size(),
                "types", saved.stream().map(c -> c.getCancellationType().name()).toList(),
                "targets", saved.stream().map(c -> c.getPayment() != null ? "payment" : "transaction").toList(),
                "cancelledBy", result.getCancelledBy());
    }
    private Object refund(String scenario) {
        var refunds = new V2RepositoryStub<>(RefundTransactionRepository.class);
        var cancellations = new V2RepositoryStub<>(TransactionCancellationRepository.class);
        var service = new RefundService(refunds.proxy(),
                new TransactionCancellationService(cancellations.proxy(), null, null, null, null, null),
                new V2RepositoryStub<>(com.sabyeguyz.clinicv2.catalog.CourseRepository.class).proxy(),
                new V2RepositoryStub<>(com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository.class).proxy());
        var paid = payment("1000.00", !scenario.equals("refund-voided-payment"));
        paid.setReceipt(new Receipt());
        refunds.on("refundedOrPendingAmountOf", a -> scenario.equals("refund-over") ? decimal("700.00") : BigDecimal.ZERO);
        var pending = new RefundTransaction();
        pending.setOriginalPayment(paid);
        pending.setRefundStatus(scenario.equals("refund-complete-twice") ? RefundStatus.Completed : RefundStatus.Pending);
        refunds.on("findById", a -> Optional.of(pending));
        if (scenario.startsWith("refund-complete")) {
            var done = service.complete(1L, 9L);
            return Map.of("status", done.getRefundStatus(), "refundedBy", done.getRefundedBy(), "hasRefundedAt", done.getRefundedAt() != null);
        }
        if (scenario.equals("refund-fail")) {
            var failed = service.fail(1L);
            return Map.of("status", failed.getRefundStatus(), "hasRefundedAt", failed.getRefundedAt() != null);
        }
        var refund = service.request(paid, decimal("400.00"), new PaymentMethod(), new Permission(), "คืนเงินตามคำขอ", 9L);
        var cancellation = (TransactionCancellation) cancellations.arguments("save").get(0)[0];
        return Map.of("status", refund.getRefundStatus(), "amount", refund.getRefundAmount(),
                "requestedBy", refund.getRequestedBy(), "hasRefundedAt", refund.getRefundedAt() != null,
                "cancellationType", cancellation.getCancellationType(), "sameCancellation", refund.getCancellation() == cancellation);
    }
    private Object revenue(String scenario) {
        return switch (scenario) {
            case "revenue-single-line" -> com.sabyeguyz.clinicv2.report.RevenueSplit.split(decimal("1500.00"),
                    List.of(line(TransactionType.CoursePurchase, "1500.00", "0.00")));
            case "revenue-mixed" -> com.sabyeguyz.clinicv2.report.RevenueSplit.split(decimal("1800.00"),
                    List.of(line(TransactionType.CoursePurchase, "1500.00", "150.00"), line(TransactionType.SingleVisit, "500.00", "50.00")));
            case "revenue-rounding" -> com.sabyeguyz.clinicv2.report.RevenueSplit.split(decimal("100.00"),
                    List.of(line(TransactionType.SingleVisit, "100.00", "0.00"), line(TransactionType.SingleVisit, "100.00", "0.00"),
                            line(TransactionType.CoursePurchase, "100.00", "0.00")));
            case "revenue-no-lines" -> com.sabyeguyz.clinicv2.report.RevenueSplit.split(decimal("250.00"), List.of());
            default -> throw new IllegalArgumentException("Unknown revenue scenario: " + scenario);
        };
    }
}
