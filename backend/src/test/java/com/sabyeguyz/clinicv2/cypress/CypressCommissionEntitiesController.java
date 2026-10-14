package com.sabyeguyz.clinicv2.cypress;

import com.sabyeguyz.clinicv2.appointment.PatientVisitRepository;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.*;
import com.sabyeguyz.clinicv2.course.CourseRefundItem;
import com.sabyeguyz.clinicv2.course.CourseRefundItemRepository;
import com.sabyeguyz.clinicv2.enums.*;
import com.sabyeguyz.clinicv2.sale.RefundTransactionRepository;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Persistence checks of the commission tables (course refund items, single-visit fee ledger, monthly payments,
 * pay adjustments and their applications). The caller creates the real prerequisites (patient, visit, sale, course,
 * refund) through the public API and passes their ids; every attempt here runs in its own transaction so a row the
 * database rejects is reported ("rejected": reason) without spoiling the next attempt.
 */
@Profile("cypress-v2")
@RestController
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/__cypress/commission-entities")
public class CypressCommissionEntitiesController {
    private final TransactionTemplate tx;
    private final CourseRefundItemRepository refundItems;
    private final RefundTransactionRepository refunds;
    private final CourseRepository courses;
    private final PatientVisitRepository visits;
    private final StaffRepository staff;
    private final TreatmentFeeRuleRepository rules;
    private final SingleVisitFeeLedgerRepository fees;
    private final StaffMonthlyPaymentRepository payments;
    private final StaffPayAdjustmentRepository adjustments;
    private final AdjustmentApplicationRepository applications;
    private final JdbcTemplate jdbc;

    public CypressCommissionEntitiesController(JdbcTemplate jdbc, TransactionTemplate tx, CourseRefundItemRepository refundItems,
            RefundTransactionRepository refunds, CourseRepository courses, PatientVisitRepository visits, StaffRepository staff,
            TreatmentFeeRuleRepository rules, SingleVisitFeeLedgerRepository fees, StaffMonthlyPaymentRepository payments,
            StaffPayAdjustmentRepository adjustments, AdjustmentApplicationRepository applications) {
        this.tx = tx; this.refundItems = refundItems; this.refunds = refunds; this.courses = courses; this.visits = visits;
        this.staff = staff; this.rules = rules; this.fees = fees; this.payments = payments; this.adjustments = adjustments;
        this.applications = applications;
        this.jdbc = jdbc;
    }

    /** Ids of the newest rows that checks need as prerequisites (the visit made by a single-visit sale, the fee rule). */
    @GetMapping("/latest")
    public Map<String, Object> latest() {
        return map("visitId", jdbc.queryForObject("select max(visit_id) from patient_visit", Long.class),
                "ruleId", jdbc.queryForObject("select max(treatment_fee_rule_id) from treatment_fee_rule", Long.class));
    }

    public record Ids(Long refundId, Long courseId, Long visitId, Long staffId, Long ruleId, Long userId) {}

    /** Runs the attempt in a new transaction; a database/validation failure becomes {"rejected": reason}. */
    private Map<String, Object> attempt(Supplier<Map<String, Object>> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (RuntimeException failure) {
            Throwable root = failure;
            while (root.getCause() != null) root = root.getCause();
            String message = root.getMessage() == null ? "" : root.getMessage();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rejected", message.replaceAll("(?s)^ERROR: ", "").split("\n")[0]);
            return out;
        }
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) out.put((String) keyValues[i], keyValues[i + 1]);
        return out;
    }

    private static BigDecimal d(String value) { return new BigDecimal(value); }

    @PostMapping("/refund-item")
    public Map<String, Object> refundItem(@RequestBody Ids ids) {
        Map<String, Object> saved = attempt(() -> {
            CourseRefundItem item = new CourseRefundItem();
            item.setRefund(refunds.findById(ids.refundId()).orElseThrow());
            item.setCourse(courses.findById(ids.courseId()).orElseThrow());
            item.setRefundAmount(d("300.00"));
            item.setCancelledVisits(2);
            Long id = refundItems.saveAndFlush(item).getId();
            var read = refundItems.findById(id).orElseThrow();
            return map("id", id, "refundId", read.getRefund().getId(), "courseId", read.getCourse().getId(),
                    "amount", read.getRefundAmount(), "cancelledVisits", read.getCancelledVisits());
        });
        Map<String, Object> out = new LinkedHashMap<>(saved);
        out.put("duplicate", attempt(() -> {
            CourseRefundItem again = new CourseRefundItem();
            again.setRefund(refunds.findById(ids.refundId()).orElseThrow());
            again.setCourse(courses.findById(ids.courseId()).orElseThrow());
            again.setRefundAmount(d("10.00"));
            again.setCancelledVisits(1);
            return map("id", refundItems.saveAndFlush(again).getId());
        }));
        out.put("zeroAmount", attempt(() -> {
            CourseRefundItem item = new CourseRefundItem();
            item.setRefund(refunds.findById(ids.refundId()).orElseThrow());
            item.setCourse(courses.findById(ids.courseId()).orElseThrow());
            item.setRefundAmount(BigDecimal.ZERO);
            item.setCancelledVisits(1);
            return map("id", refundItems.saveAndFlush(item).getId());
        }));
        return out;
    }

    private SingleVisitFeeLedger fee(Ids ids, AllocationEntryType type, String amount, int version) {
        SingleVisitFeeLedger row = new SingleVisitFeeLedger();
        row.setVisit(visits.findById(ids.visitId()).orElseThrow());
        row.setStaff(staff.findById(ids.staffId()).orElseThrow());
        row.setTreatmentFeeRule(rules.findById(ids.ruleId()).orElseThrow());
        row.setServiceDate(LocalDate.of(2026, 10, 15));
        row.setFeeType(FeeType.Percentage);
        row.setFeeValue(d("10"));
        row.setCalculationBase(d("1000.00"));
        row.setFeeAmount(d(amount));
        row.setEntryType(type);
        row.setFeeVersion(version);
        row.setCreatedAt(Instant.now());
        row.setCreatedBy(ids.userId());
        return row;
    }

    @PostMapping("/single-visit-fee")
    public Map<String, Object> singleVisitFee(@RequestBody Ids ids) {
        Map<String, Object> out = new LinkedHashMap<>();
        Long[] allocationId = new Long[1];
        out.put("allocation", attempt(() -> {
            var saved = fees.saveAndFlush(fee(ids, AllocationEntryType.Allocation, "100.00", 1));
            allocationId[0] = saved.getId();
            var read = fees.findById(saved.getId()).orElseThrow();
            return map("id", read.getId(), "entryType", read.getEntryType().name(), "feeAmount", read.getFeeAmount(),
                    "feeType", read.getFeeType().name(), "version", read.getFeeVersion(), "month", read.getServiceDate().toString());
        }));
        out.put("reversalWithoutReason", attempt(() -> {
            var row = fee(ids, AllocationEntryType.Reversal, "-100.00", 1);
            row.setReversesFee(fees.findById(allocationId[0]).orElseThrow());
            return map("id", fees.saveAndFlush(row).getId());
        }));
        out.put("reversal", attempt(() -> {
            var row = fee(ids, AllocationEntryType.Reversal, "-100.00", 1);
            row.setReversesFee(fees.findById(allocationId[0]).orElseThrow());
            row.setReason("บันทึกผิด");
            var saved = fees.saveAndFlush(row);
            return map("id", saved.getId(), "reversesAllocation", saved.getReversesFee().getId().equals(allocationId[0]));
        }));
        out.put("secondReversalOfTheSameFee", attempt(() -> {
            var row = fee(ids, AllocationEntryType.Reversal, "-100.00", 2);
            row.setReversesFee(fees.findById(allocationId[0]).orElseThrow());
            row.setReason("ซ้ำ");
            return map("id", fees.saveAndFlush(row).getId());
        }));
        out.put("negativeAllocation", attempt(() -> map("id", fees.saveAndFlush(fee(ids, AllocationEntryType.Allocation, "-5.00", 3)).getId())));
        return out;
    }

    private StaffMonthlyPayment payment(Ids ids, String month, PaymentStatus status) {
        StaffMonthlyPayment row = new StaffMonthlyPayment();
        row.setStaff(staff.findById(ids.staffId()).orElseThrow());
        row.setPaymentMonth(month);
        row.setPaymentStatus(status);
        row.setStatusChangedBy(ids.userId());
        row.setStatusChangedAt(Instant.now());
        return row;
    }

    private static void amounts(StaffMonthlyPayment row, String course, String single, String substitute, String adjustment, String net) {
        row.setCourseCommissionAmount(d(course));
        row.setSingleVisitFeeAmount(d(single));
        row.setSubstituteFeeAmount(d(substitute));
        row.setAdjustmentAmount(d(adjustment));
        row.setNetPaymentAmount(d(net));
    }

    private StaffPayAdjustment adjustment(Ids ids, String original, String applied, String amount, String reason) {
        StaffPayAdjustment row = new StaffPayAdjustment();
        row.setStaff(staff.findById(ids.staffId()).orElseThrow());
        row.setOriginalMonth(original);
        row.setAppliedMonth(applied);
        row.setAmount(d(amount));
        row.setReason(reason);
        row.setProcessedAmount(BigDecimal.ZERO);
        row.setRemainingAmount(d(amount).abs());
        row.setCreatedAt(Instant.now());
        row.setCreatedBy(ids.userId());
        return row;
    }

    @PostMapping("/payments")
    public Map<String, Object> monthlyPayments(@RequestBody Ids ids) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("open", attempt(() -> {
            var saved = payments.saveAndFlush(payment(ids, "2026-10", PaymentStatus.Open));
            var read = payments.findById(saved.getId()).orElseThrow();
            return map("id", read.getId(), "month", read.getPaymentMonth(), "status", read.getPaymentStatus().name(),
                    "net", read.getNetPaymentAmount());
        }));
        out.put("paidWithoutAmounts", attempt(() -> {
            var row = payment(ids, "2026-09", PaymentStatus.Paid);
            row.setPaidAt(Instant.now());
            return map("id", payments.saveAndFlush(row).getId());
        }));
        out.put("paidWithWrongNet", attempt(() -> {
            var row = payment(ids, "2026-09", PaymentStatus.Paid);
            row.setPaidAt(Instant.now());
            amounts(row, "2500.00", "0.00", "500.00", "-140.00", "2900.00");
            return map("id", payments.saveAndFlush(row).getId());
        }));
        out.put("paidWithoutPaidAt", attempt(() -> {
            var row = payment(ids, "2026-09", PaymentStatus.Paid);
            amounts(row, "2500.00", "0.00", "500.00", "-140.00", "2860.00");
            return map("id", payments.saveAndFlush(row).getId());
        }));
        out.put("paid", attempt(() -> {
            var row = payment(ids, "2026-09", PaymentStatus.Paid);
            row.setPaidAt(Instant.now());
            amounts(row, "2500.00", "0.00", "500.00", "-140.00", "2860.00");
            var saved = payments.saveAndFlush(row);
            var read = payments.findById(saved.getId()).orElseThrow();
            return map("id", read.getId(), "net", read.getNetPaymentAmount(), "status", read.getPaymentStatus().name());
        }));
        out.put("secondRowForTheSameStaffAndMonth", attempt(() -> map("id", payments.saveAndFlush(payment(ids, "2026-10", PaymentStatus.Open)).getId())));
        return out;
    }

    @PostMapping("/adjustments")
    public Map<String, Object> payAdjustments(@RequestBody Ids ids) {
        Map<String, Object> out = new LinkedHashMap<>();
        Long[] adjustmentId = new Long[1];
        Long[] paymentId = new Long[1];
        attempt(() -> { paymentId[0] = payments.saveAndFlush(payment(ids, "2026-11", PaymentStatus.Open)).getId(); return map(); });
        out.put("minusFromSeptember", attempt(() -> {
            var row = adjustment(ids, "2026-09", "2026-10", "-140.00", "บันทึก Visit ซ้ำ");
            row.setCourse(courses.findById(ids.courseId()).orElseThrow());
            var saved = adjustments.saveAndFlush(row);
            adjustmentId[0] = saved.getId();
            var read = adjustments.findById(saved.getId()).orElseThrow();
            return map("id", read.getId(), "amount", read.getAmount(), "status", read.getAdjustmentStatus().name(),
                    "remaining", read.getRemainingAmount(), "originalMonth", read.getOriginalMonth(), "appliedMonth", read.getAppliedMonth());
        }));
        out.put("appliedMonthNotAfterOriginal", attempt(() -> {
            var row = adjustment(ids, "2026-09", "2026-09", "-10.00", "x");
            row.setCourse(courses.findById(ids.courseId()).orElseThrow());
            return map("id", adjustments.saveAndFlush(row).getId());
        }));
        out.put("zeroAmount", attempt(() -> {
            var row = adjustment(ids, "2026-09", "2026-10", "0.00", "x");
            row.setCourse(courses.findById(ids.courseId()).orElseThrow());
            return map("id", adjustments.saveAndFlush(row).getId());
        }));
        out.put("noSource", attempt(() -> map("id", adjustments.saveAndFlush(adjustment(ids, "2026-09", "2026-10", "-10.00", "x")).getId())));
        out.put("blankReason", attempt(() -> {
            var row = adjustment(ids, "2026-09", "2026-10", "-10.00", " ");
            row.setCourse(courses.findById(ids.courseId()).orElseThrow());
            return map("id", adjustments.saveAndFlush(row).getId());
        }));
        out.put("approvedStatusWithoutApprover", attempt(() -> {
            var row = adjustment(ids, "2026-09", "2026-10", "-10.00", "x");
            row.setCourse(courses.findById(ids.courseId()).orElseThrow());
            row.setAdjustmentStatus(AdjustmentStatus.Approved);
            return map("id", adjustments.saveAndFlush(row).getId());
        }));
        out.put("remainingNotMatchingBalance", attempt(() -> {
            var row = adjustment(ids, "2026-09", "2026-10", "-10.00", "x");
            row.setCourse(courses.findById(ids.courseId()).orElseThrow());
            row.setRemainingAmount(d("3.00"));
            return map("id", adjustments.saveAndFlush(row).getId());
        }));
        out.put("draftApplication", attempt(() -> {
            var row = new AdjustmentApplication();
            row.setAdjustment(adjustments.findById(adjustmentId[0]).orElseThrow());
            row.setMonthlyPayment(payments.findById(paymentId[0]).orElseThrow());
            row.setAmount(d("-140.00"));
            row.setCreatedAt(Instant.now());
            row.setCreatedBy(ids.userId());
            var saved = applications.saveAndFlush(row);
            var read = applications.findById(saved.getId()).orElseThrow();
            return map("id", read.getId(), "status", read.getApplicationStatus().name(), "amount", read.getAmount());
        }));
        out.put("appliedWithoutApprover", attempt(() -> {
            var row = new AdjustmentApplication();
            row.setAdjustment(adjustments.findById(adjustmentId[0]).orElseThrow());
            row.setMonthlyPayment(payments.findById(paymentId[0]).orElseThrow());
            row.setAmount(d("-140.00"));
            row.setApplicationStatus(ApplicationStatus.Applied);
            row.setCreatedAt(Instant.now());
            row.setCreatedBy(ids.userId());
            return map("id", applications.saveAndFlush(row).getId());
        }));
        out.put("appliedDeductionWithoutReference", attempt(() -> {
            var row = new AdjustmentApplication();
            row.setAdjustment(adjustments.findById(adjustmentId[0]).orElseThrow());
            row.setMonthlyPayment(payments.findById(paymentId[0]).orElseThrow());
            row.setAmount(d("-140.00"));
            row.setApplicationStatus(ApplicationStatus.Applied);
            row.setApprovedBy(ids.userId());
            row.setApprovedAt(Instant.now());
            row.setCreatedAt(Instant.now());
            row.setCreatedBy(ids.userId());
            return map("id", applications.saveAndFlush(row).getId());
        }));
        out.put("appliedDeductionWithReference", attempt(() -> {
            var row = new AdjustmentApplication();
            row.setAdjustment(adjustments.findById(adjustmentId[0]).orElseThrow());
            row.setMonthlyPayment(payments.findById(paymentId[0]).orElseThrow());
            row.setAmount(d("-140.00"));
            row.setApplicationStatus(ApplicationStatus.Applied);
            row.setApprovedBy(ids.userId());
            row.setApprovedAt(Instant.now());
            row.setApprovalReference("อนุมัติโดยผู้จัดการ");
            row.setCreatedAt(Instant.now());
            row.setCreatedBy(ids.userId());
            var saved = applications.saveAndFlush(row);
            return map("id", saved.getId(), "status", applications.findById(saved.getId()).orElseThrow().getApplicationStatus().name());
        }));
        return out;
    }
}
