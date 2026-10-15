package com.sabyeguyz.clinicv2.report;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.audit.AuditLogRepository;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration-based customer counts and recorded activity; independent of commission calculations. */
@Service
@Transactional(readOnly = true)
public class DashboardMetricsService {
    public record Activity(long newCustomers, long returningCustomers, long courseRenewals, long packageUpdates) {}
    private final PatientRepository patients;
    private final AuditLogRepository audit;
    private final JdbcTemplate jdbc;

    public DashboardMetricsService(PatientRepository patients, AuditLogRepository audit, JdbcTemplate jdbc) {
        this.patients = patients;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public Activity activity(Instant from, Instant to, Long branchId, List<PatientVisit> attended) {
        long returning = attended.stream().filter(v -> v.getPatient().getRegisteredAt().isBefore(from))
                .map(v -> v.getPatient().getId()).distinct().count();
        String query = "SELECT count(*) FROM course_extension e JOIN course c ON c.course_id=e.course_id "
                + "WHERE e.extended_at>=? AND e.extended_at<?";
        Object[] dates = { LocalDateTime.ofInstant(from, ZoneOffset.UTC), LocalDateTime.ofInstant(to, ZoneOffset.UTC) };
        Long renewals = branchId == null ? jdbc.queryForObject(query, Long.class, dates)
                : jdbc.queryForObject(query + " AND c.branch_id=?", Long.class, dates[0], dates[1], branchId);
        // Templates are shared clinic-wide. An UPDATE event represents a changed, successfully saved template.
        long updates = audit.countByTableNameAndActionAndChangedAtGreaterThanEqualAndChangedAtLessThan(
                "course_template", AuditAction.UPDATE, from, to);
        return new Activity(patients.countRegistrations(from, to, branchId), returning,
                renewals == null ? 0 : renewals, updates);
    }
}
