package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.ValidityUnit;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Extending the expiry of a course: only the original owner can ask, only a Manager can approve. The expiry date
 * belongs to the course itself, so once approved every holder of the course (the owner and the people it was shared
 * with) uses the new date together.
 */
@Transactional
@RestController
@RequestMapping("/api/course-extensions")
public class CourseExtensionController {
    private final JdbcTemplate jdbc;
    private final CourseRepository courses;
    private final BranchAccessPolicy branchAccess;
    private final CurrentActor actor;
    private final AuditLogService audit;

    public CourseExtensionController(JdbcTemplate jdbc, CourseRepository courses, BranchAccessPolicy branchAccess,
                                      CurrentActor actor, AuditLogService audit) {
        this.jdbc = jdbc;
        this.courses = courses;
        this.branchAccess = branchAccess;
        this.actor = actor;
        this.audit = audit;
    }

    /** requestedByPatientId is the patient who is asking; when given it must be the owner of the course. */
    public record CreateRequest(Long courseId, Integer value, String unit, String reason, Long requestedByPatientId) {}

    public record DecisionRequest(String note) {}

    public record Response(Long id, Long courseId, Long requestedPatientId, int value, String unit, String reason,
                           String status, Instant requestedAt, Instant decidedAt, String decisionNote,
                           LocalDate oldExpiry, LocalDate newExpiry) {}

    private static final String SELECT = "SELECT r.request_id, r.course_id, r.requested_patient_id, r.extension_value, "
            + "r.extension_unit::text AS unit, r.reason, r.request_status, r.requested_at, r.decided_at, r.decision_note, "
            + "e.old_expiry_date, e.new_expiry_date FROM course_extension_request r "
            + "LEFT JOIN course_extension e ON e.extension_id = r.extension_id ";

    private static final RowMapper<Response> MAPPER = (rs, row) -> new Response(
            rs.getLong("request_id"), rs.getLong("course_id"), rs.getLong("requested_patient_id"),
            rs.getInt("extension_value"), rs.getString("unit"), rs.getString("reason"), rs.getString("request_status"),
            instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("decided_at")),
            rs.getString("decision_note"),
            rs.getDate("old_expiry_date") == null ? null : rs.getDate("old_expiry_date").toLocalDate(),
            rs.getDate("new_expiry_date") == null ? null : rs.getDate("new_expiry_date").toLocalDate());

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** Requests of one course (newest first); a Manager can also list every pending request of the clinic. */
    @GetMapping
    public List<Response> list(@RequestParam(required = false) Long courseId,
                               @RequestParam(required = false) String status) {
        if (courseId != null) {
            branchAccess.requireAccess(find(courseId).getBranch());
            return jdbc.query(SELECT + "WHERE r.course_id = ? ORDER BY r.request_id DESC", MAPPER, courseId);
        }
        // scope(null) is null only for a Manager (every branch); anyone else gets their home branch
        if (branchAccess.scope(null) != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ดูรายการต่ออายุทั้งหมดได้เฉพาะ Manager");
        }
        if (status != null && !status.isBlank()) {
            return jdbc.query(SELECT + "WHERE r.request_status = ? ORDER BY r.request_id DESC", MAPPER, status.trim());
        }
        return jdbc.query(SELECT + "ORDER BY r.request_id DESC", MAPPER);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody CreateRequest request) {
        if (request.courseId() == null) throw new IllegalArgumentException("ต้องระบุคอร์ส");
        int value = request.value() == null ? 0 : request.value();
        if (value < 1) throw new IllegalArgumentException("ต้องระบุระยะเวลาที่ต่ออายุอย่างน้อย 1");
        ValidityUnit unit = parseUnit(request.unit());
        Course course = find(request.courseId());
        branchAccess.requireAccess(course.getBranch());
        if (course.getCourseStatus() != CourseStatus.Active) {
            throw new IllegalArgumentException("ต่ออายุได้เฉพาะคอร์สที่ยังใช้งานอยู่");
        }
        Long ownerId = course.getPurchaserPatient().getId();
        if (request.requestedByPatientId() != null && !ownerId.equals(request.requestedByPatientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "เฉพาะเจ้าของคอร์สเดิมเท่านั้นที่ขอต่ออายุได้ ผู้ที่ได้รับสิทธิ์แชร์ขอต่ออายุเองไม่ได้");
        }
        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM course_extension_request WHERE course_id = ? AND request_status = 'Pending'",
                Integer.class, course.getId());
        if (pending != null && pending > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "คอร์สนี้มีคำขอต่ออายุที่รออนุมัติอยู่แล้ว");
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (reason != null && reason.length() > 255) throw new IllegalArgumentException("เหตุผลยาวเกิน 255 ตัวอักษร");
        Long id = jdbc.queryForObject("INSERT INTO course_extension_request (course_id, requested_patient_id, extension_value, "
                        + "extension_unit, reason, requested_by) VALUES (?, ?, ?, ?::validity_unit_enum, ?, ?) RETURNING request_id",
                Long.class, course.getId(), ownerId, value, unit.name(), reason, actor.currentId());
        audit.record("course_extension_request", String.valueOf(id), AuditAction.INSERT, null,
                "course " + course.getId() + " +" + value + " " + unit, actor.currentId());
        return get(id);
    }

    /** Manager only: moves the expiry date of the course and records what changed. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public Response approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        Row row = lockPending(id);
        Course course = courses.findByIdForUpdate(row.courseId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        if (course.getCourseStatus() != CourseStatus.Active) {
            throw new IllegalArgumentException("ต่ออายุได้เฉพาะคอร์สที่ยังใช้งานอยู่");
        }
        LocalDate oldExpiry = course.getExpiryDate();
        // An expired course is extended from today, not from a date that already passed.
        LocalDate base = oldExpiry.isBefore(ClinicTime.today()) ? ClinicTime.today() : oldExpiry;
        LocalDate newExpiry = switch (row.unit()) {
            case Day -> base.plusDays(row.value());
            case Month -> base.plusMonths(row.value());
            case Year -> base.plusYears(row.value());
        };
        Long extensionId = jdbc.queryForObject("INSERT INTO course_extension (course_id, extension_value, extension_unit, "
                        + "old_expiry_date, new_expiry_date, extended_by, reason) VALUES (?, ?, ?::validity_unit_enum, ?, ?, ?, ?) "
                        + "RETURNING extension_id",
                Long.class, course.getId(), row.value(), row.unit().name(), oldExpiry, newExpiry, actor.currentId(), row.reason());
        course.setExpiryDate(newExpiry);
        courses.save(course);
        jdbc.update("UPDATE course_extension_request SET request_status = 'Approved', decided_by = ?, "
                        + "decided_at = CURRENT_TIMESTAMP, decision_note = ?, extension_id = ? WHERE request_id = ?",
                actor.currentId(), note(request), extensionId, id);
        audit.record("course", String.valueOf(course.getId()), AuditAction.UPDATE, "expiry " + oldExpiry,
                "expiry " + newExpiry + " (extension request " + id + ")", actor.currentId());
        return get(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public Response reject(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        lockPending(id);
        jdbc.update("UPDATE course_extension_request SET request_status = 'Rejected', decided_by = ?, "
                + "decided_at = CURRENT_TIMESTAMP, decision_note = ? WHERE request_id = ?", actor.currentId(), note(request), id);
        audit.record("course_extension_request", String.valueOf(id), AuditAction.UPDATE, "Pending", "Rejected", actor.currentId());
        return get(id);
    }

    private record Row(Long courseId, int value, ValidityUnit unit, String status, String reason) {}

    private Row lockPending(Long id) {
        List<Row> rows = jdbc.query("SELECT course_id, extension_value, extension_unit::text AS unit, request_status, reason "
                        + "FROM course_extension_request WHERE request_id = ? FOR UPDATE",
                (rs, n) -> new Row(rs.getLong("course_id"), rs.getInt("extension_value"),
                        ValidityUnit.valueOf(rs.getString("unit")), rs.getString("request_status"), rs.getString("reason")), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบคำขอต่ออายุ");
        if (!"Pending".equals(rows.get(0).status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "คำขอนี้ถูกดำเนินการไปแล้ว");
        }
        return rows.get(0);
    }

    private Response get(Long id) {
        return jdbc.queryForObject(SELECT + "WHERE r.request_id = ?", MAPPER, id);
    }

    private Course find(Long courseId) {
        return courses.findById(courseId).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
    }

    private static String note(DecisionRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()) return null;
        String text = request.note().trim();
        if (text.length() > 255) throw new IllegalArgumentException("หมายเหตุยาวเกิน 255 ตัวอักษร");
        return text;
    }

    private static ValidityUnit parseUnit(String text) {
        if (text == null || text.isBlank()) return ValidityUnit.Day;
        for (ValidityUnit unit : ValidityUnit.values()) {
            if (unit.name().equalsIgnoreCase(text.trim())) return unit;
        }
        throw new IllegalArgumentException("หน่วยต้องเป็น Day, Month หรือ Year");
    }
}
