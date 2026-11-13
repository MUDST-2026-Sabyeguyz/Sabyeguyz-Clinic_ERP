package com.sabyeguyz.clinicv2.cypress;

import com.sabyeguyz.clinicv2.sale.*;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Profile("cypress-v2")
@RestController
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/__cypress/payment-policy")
public class CypressPaymentPolicyController {
    private final SalesTransactionRepository sales;
    private final PaymentTransactionService payments;
    private final PaymentMethodRepository methods;
    private final CurrentActor actor;
    private final JdbcTemplate jdbc;
    private final com.sabyeguyz.clinicv2.course.CoursePurchaseController courses;
    private final java.util.concurrent.ConcurrentMap<Long, HeldUsage> held = new java.util.concurrent.ConcurrentHashMap<>();
    private record HeldUsage(int pid, java.util.concurrent.CountDownLatch release) {}
    public CypressPaymentPolicyController(SalesTransactionRepository sales, PaymentTransactionService payments,
            PaymentMethodRepository methods, CurrentActor actor, JdbcTemplate jdbc,
            com.sabyeguyz.clinicv2.course.CoursePurchaseController courses) {
        this.sales = sales; this.payments = payments; this.methods = methods; this.actor = actor; this.jdbc = jdbc; this.courses = courses;
    }
    public record RecordRequest(String method, BigDecimal amount) {}
    @PostMapping("/{saleId}/record")
    @Transactional
    public Map<String,Object> record(@PathVariable Long saleId, @RequestBody RecordRequest request) {
        var sale = sales.findById(saleId).orElseThrow();
        var method = methods.findByMethodNameIgnoreCase(PaymentMethod.canonicalName(request.method())).orElseThrow();
        var payment = payments.record(sale.getPatient(), sale.getBranch(), sale.getReceipt(), request.amount(), method, actor.currentId());
        return Map.of("id", payment.getId());
    }
    /** Arrange historical data only; new production Assessment sales remain rejected. */
    @PostMapping("/{saleId}/historical-assessment")
    @Transactional
    public Map<String,Object> historicalAssessment(@PathVariable Long saleId) {
        return Map.of("updated", jdbc.update(
                "update sales_transaction set transaction_type = 'Assessment' where transaction_id = ?", saleId));
    }
    /** Runs the real usage handler, holding its transaction until Cypress observes the refund waiting. */
    @PostMapping("/held-usage/{courseId}")
    @Transactional
    public Map<String,Object> heldUsage(@PathVariable Long courseId) throws InterruptedException {
        courses.use(new com.sabyeguyz.clinicv2.course.CoursePurchaseController.UseRequest(courseId, null, null, null, null, null));
        var state = new HeldUsage(jdbc.queryForObject("select pg_backend_pid()", Integer.class), new java.util.concurrent.CountDownLatch(1));
        held.put(courseId, state);
        try {
            if (!state.release().await(20, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            return Map.of("used", true);
        } finally { held.remove(courseId); }
    }
    @GetMapping("/held-usage/{courseId}")
    public Map<String,Object> heldStatus(@PathVariable Long courseId) {
        var state = held.get(courseId);
        boolean blocked = state != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))", Boolean.class, state.pid()));
        return Map.of("held", state != null, "blocked", blocked);
    }
    @PostMapping("/held-usage/{courseId}/release")
    public Map<String,Object> release(@PathVariable Long courseId) {
        var state = held.get(courseId);
        if (state != null) state.release().countDown();
        return Map.of("released", state != null);
    }
    @GetMapping("/counts")
    public Map<String,Object> counts() {
        return Map.of("receipts", count("receipt"), "sales", count("sales_transaction"), "payments", count("payment_transaction"),
                "refunds", count("refund_transaction"), "cancellations", count("transaction_cancellation"), "allocations", count("commission_allocation_ledger"));
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
}
