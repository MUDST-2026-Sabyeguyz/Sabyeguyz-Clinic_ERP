package com.sabyeguyz.clinicv2.sale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.audit.AuditLogRepository;
import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Durable request receipts in the existing append-only audit store, authorized by the project owner. */
@Service
public class CoursePaymentCollectionRequestService {
    private static final String AUDIT_TABLE = "course_payment_collection_request";
    private final CoursePaymentCollectionService collections;
    private final PatientRepository patients;
    private final BranchRepository branches;
    private final PaymentMethodRepository methods;
    private final BranchAccessPolicy access;
    private final CurrentActor actor;
    private final AuditLogRepository auditRows;
    private final AuditLogService audit;
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public CoursePaymentCollectionRequestService(CoursePaymentCollectionService collections,
            PatientRepository patients, BranchRepository branches, PaymentMethodRepository methods,
            BranchAccessPolicy access, CurrentActor actor, AuditLogRepository auditRows,
            AuditLogService audit, JdbcTemplate db, ObjectMapper json) {
        this.collections = collections; this.patients = patients; this.branches = branches;
        this.methods = methods; this.access = access; this.actor = actor; this.auditRows = auditRows;
        this.audit = audit; this.db = db; this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CoursePaymentCollectionService.Result collect(String key, CoursePaymentCollectionRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new IllegalArgumentException("ต้องระบุ Idempotency-Key ความยาว 8–128 ตัวอักษร (A-Z, a-z, 0-9, ., _, :, -)");
        }
        Long actorId = actor.requireId();
        var branch = branches.findByBranchCode(request.branchCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา"));
        // Authorization is re-evaluated even when returning a committed response.
        access.requireAccess(branch);
        var patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        var method = methods.findByMethodNameIgnoreCase(PaymentMethod.canonicalName(request.paymentMethodCode()))
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน"));
        Discount discount = Discount.of(request.billDiscountType(), request.billDiscountValue());
        String label = request.paymentLabelCode().trim().toUpperCase(Locale.ROOT);
        var items = request.items().stream().sorted(Comparator.comparing(CoursePaymentCollectionRequest.Item::courseId))
                .map(item -> new ReceiptCoursePaymentService.ItemRequest(item.courseId(), item.amount())).toList();

        Map<String, Object> payload = new TreeMap<>();
        payload.put("patientId", patient.getId()); payload.put("branchId", branch.getId());
        payload.put("items", items.stream().map(item -> List.of(item.courseId(), money(item.amount()))).toList());
        payload.put("discountType", discount.type() == null ? null : discount.type().name());
        payload.put("discountValue", discount.isNone() ? null : money(discount.value()));
        payload.put("paymentLabel", label); payload.put("paymentMethodId", method.getId());
        payload.put("paymentAmount", money(request.paymentAmount()));
        String fingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(digest(encode(payload)));
        byte[] scopedKey = digest(actorId + "\n" + key);
        String recordId = Base64.getUrlEncoder().withoutPadding().encodeToString(scopedKey);

        // PostgreSQL transaction locks serialize identical requests across processes/replicas.
        // A 64-bit lock collision only serializes unrelated keys: the full digest identifies the audit row.
        db.query("select pg_advisory_xact_lock(?)", result -> { return null; }, ByteBuffer.wrap(scopedKey).getLong());
        var prior = auditRows.findByTableNameAndRecordIdAndActionOrderByIdAsc(AUDIT_TABLE, recordId, AuditAction.INSERT);
        if (!prior.isEmpty()) {
            if (prior.size() != 1) throw new IllegalStateException("ข้อมูลอ้างอิงคำขอซ้ำไม่ถูกต้อง กรุณาติดต่อผู้ดูแล");
            try {
                var stored = json.readTree(prior.get(0).getNewValue());
                if (!fingerprint.equals(stored.path("fingerprint").asText())) {
                    throw new IllegalStateException("Idempotency-Key นี้ถูกใช้กับข้อมูลอื่นแล้ว");
                }
                return json.treeToValue(stored.path("result"), CoursePaymentCollectionService.Result.class);
            } catch (JsonProcessingException corrupt) {
                throw new IllegalStateException("ไม่สามารถอ่านผลคำขอเดิมได้", corrupt);
            }
        }
        var result = collections.collect(patient, branch, items, discount, label, method, request.paymentAmount());
        audit.recordChange(AUDIT_TABLE, recordId, AuditAction.INSERT, null,
                AuditLogService.fields("version", 1, "fingerprint", fingerprint, "result", result), actorId);
        // Receipt, payment and replay record commit together; any failure rolls them all back.
        auditRows.flush();
        return result;
    }

    private static String money(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException invalid) { throw new IllegalArgumentException("ข้อมูลคำขอไม่ถูกต้อง", invalid); }
    }
    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
