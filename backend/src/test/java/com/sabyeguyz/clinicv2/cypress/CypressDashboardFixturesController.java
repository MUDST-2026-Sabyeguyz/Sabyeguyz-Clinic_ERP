package com.sabyeguyz.clinicv2.cypress;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Date boundaries for real PostgreSQL dashboard tests; never included in the production jar. */
@Profile("cypress-v2")
@PreAuthorize("hasRole('MANAGER')")
@Transactional
@RestController
@RequestMapping("/__cypress/dashboard")
public class CypressDashboardFixturesController {
    private final JdbcTemplate jdbc;
    public CypressDashboardFixturesController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Dates(Long patientId, Instant registeredAt, Instant visitAt, Instant paymentAt, LocalDate saleDate) {}
    @PostMapping("/dates")
    public void dates(@RequestBody Dates request) {
        if (request.registeredAt() != null) jdbc.update("UPDATE patient SET registered_at=?::timestamptz AT TIME ZONE 'UTC' WHERE patient_id=?", request.registeredAt().toString(), request.patientId());
        if (request.visitAt() != null) {
            jdbc.update("""
            UPDATE patient_visit
            SET checkout_time = CASE
                    WHEN checkout_time IS NULL THEN NULL
                    ELSE (?::timestamptz AT TIME ZONE 'UTC')
                         + (checkout_time - visit_date)
                END,
                visit_date = ?::timestamptz AT TIME ZONE 'UTC'
            WHERE patient_id = ?
            """,
                    request.visitAt().toString(),
                    request.visitAt().toString(),
                    request.patientId());
        }
        if (request.paymentAt() != null) jdbc.update("UPDATE payment_transaction SET payment_date=?::timestamptz AT TIME ZONE 'UTC' WHERE patient_id=?", request.paymentAt().toString(), request.patientId());
        if (request.saleDate() != null) jdbc.update("UPDATE receipt SET sale_date=? WHERE patient_id=?", request.saleDate().atStartOfDay(), request.patientId());
    }

    public record Extension(Long courseId, Instant extendedAt) {}
    @PostMapping("/extension")
    public void extension(@RequestBody Extension request) {
        jdbc.update("""
            INSERT INTO course_extension(course_id,extension_value,extension_unit,old_expiry_date,new_expiry_date,extended_by,extended_at)
            SELECT course_id,1,'Day',expiry_date,expiry_date+1,(SELECT user_id FROM app_user WHERE username='system'),
                   ?::timestamptz AT TIME ZONE 'UTC' FROM course WHERE course_id=?
            """, request.extendedAt().toString(), request.courseId());
    }
}
