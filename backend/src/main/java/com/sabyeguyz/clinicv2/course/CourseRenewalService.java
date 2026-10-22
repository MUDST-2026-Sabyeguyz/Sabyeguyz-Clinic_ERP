package com.sabyeguyz.clinicv2.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.audit.AuditLog;
import com.sabyeguyz.clinicv2.audit.AuditLogRepository;
import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Extends an owner's existing course; never adds visits or creates a new sale. */
@Service
public class CourseRenewalService {
    private final CourseRepository courses;
    private final BranchAccessPolicy access;
    private final CurrentActor actor;
    private final AuditLogService audit;
    private final AuditLogRepository auditRows;
    private final ObjectMapper json;

    public CourseRenewalService(CourseRepository courses, BranchAccessPolicy access, CurrentActor actor,
                                AuditLogService audit, AuditLogRepository auditRows, ObjectMapper json) {
        this.courses = courses; this.access = access; this.actor = actor;
        this.audit = audit; this.auditRows = auditRows; this.json = json;
    }
    public record RenewalRequest(Long patientId, LocalDate expectedExpiryDate, LocalDate newExpiryDate, String reason) {}
    public record RenewalResult(Long coursePurchaseId, LocalDate expiryDate) {}
    public record RenewalHistory(Long id, String oldExpiryDate, String newExpiryDate, String reason, Long changedBy, Instant changedAt) {}

    @Transactional
    public RenewalResult renew(Long id, RenewalRequest request) {
        requireManager();
        if (request == null || request.patientId() == null || request.expectedExpiryDate() == null
                || request.newExpiryDate() == null || request.reason() == null || request.reason().isBlank())
            throw new IllegalArgumentException("ต้องระบุเจ้าของคอร์ส วันหมดอายุเดิม วันใหม่ และเหตุผล");
        String reason = request.reason().trim();
        if (reason.length() > 1000) throw new IllegalArgumentException("เหตุผลต้องไม่เกิน 1000 ตัวอักษร");
        // Serialize expiry edits with course use/cancellation and reject stale forms.
        Course course = courses.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        access.requireAccess(course.getBranch());
        if (!course.getPurchaserPatient().getId().equals(request.patientId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ต่ออายุได้จากข้อมูลเจ้าของคอร์สเท่านั้น");
        if (course.getCourseStatus() != CourseStatus.Active || course.getRemainingVisits() <= 0)
            throw new IllegalStateException("ต่ออายุได้เฉพาะคอร์สที่ไม่ถูกยกเลิก/คืนเงินและยังมีสิทธิ์รวมเหลือ");
        if (!request.newExpiryDate().isAfter(request.expectedExpiryDate())
                || !request.newExpiryDate().isAfter(ClinicTime.today()))
            throw new IllegalArgumentException("วันหมดอายุใหม่ต้องหลังวันเดิมและหลังวันนี้");
        LocalDate before = course.getExpiryDate();
        // A network retry after a committed renewal returns success without writing another audit row.
        if (before.equals(request.newExpiryDate())) return new RenewalResult(id, before);
        if (!before.equals(request.expectedExpiryDate()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "วันหมดอายุเปลี่ยนไปแล้ว กรุณาโหลดคอร์สใหม่ก่อนต่ออายุ");
        course.setExpiryDate(request.newExpiryDate());
        course.setCourseUpdateAt(Instant.now());
        course.setCourseUpdateBy(actor.requireId());
        courses.save(course);
        // Audit expiry only: patient entitlement and commission ledgers must remain unchanged.
        audit.recordChange("course_renewal", String.valueOf(id), AuditAction.UPDATE,
                AuditLogService.fields("expiryDate", before),
                AuditLogService.fields("expiryDate", request.newExpiryDate(), "reason", reason,
                        "patientId", request.patientId()), actor.requireId());
        return new RenewalResult(id, request.newExpiryDate());
    }

    @Transactional(readOnly = true)
    public List<RenewalHistory> history(Long id) {
        requireManager();
        Course course = courses.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        access.requireAccess(course.getBranch());
        return auditRows.findByTableNameAndRecordIdOrderByChangedAtDesc("course_renewal", String.valueOf(id))
                .stream().map(this::historyRow).toList();
    }
    private void requireManager() {
        if (!access.isManager()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "เฉพาะผู้จัดการเท่านั้นที่ต่ออายุหรือตรวจประวัติการต่ออายุคอร์สได้");
    }
    private RenewalHistory historyRow(AuditLog row) {
        try {
            var before = json.readTree(row.getOldValue());
            var after = json.readTree(row.getNewValue());
            return new RenewalHistory(row.getId(), before.path("expiryDate").asText(),
                    after.path("expiryDate").asText(), after.path("reason").asText(), row.getChangedBy(), row.getChangedAt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("อ่านประวัติการต่ออายุคอร์สไม่สำเร็จ", error);
        }
    }
}
