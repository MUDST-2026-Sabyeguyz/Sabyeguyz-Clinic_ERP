package com.sabyeguyz.clinic.cypress;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Fixture utilities exist only in src/test, on loopback, with manager authorization. */
@Profile("cypress")
@RestController
@RequestMapping("/__cypress")
@PreAuthorize("hasRole('MANAGER')")
public class CypressFixturesController {
    private final JdbcTemplate jdbc;
    private final CourseRepository courses;

    public CypressFixturesController(DataSource dataSource, CourseRepository courses) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().equals("jdbc:h2:mem:clinic_cypress")) {
                throw new IllegalStateException("Cypress fixtures require the dedicated in-memory database");
            }
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.courses = courses;
    }

    @PostMapping("/reset")
    @Transactional
    public Map<String, Object> reset() {
        // Child-first deletion preserves foreign keys and the migration seed catalog/users.
        for (String table : new String[] { "commission_allocation_ledger", "course_transfers", "audit_logs",
                "sale_payments", "sale_items", "sales", "course_movements", "course_purchases",
                "treatments", "appointments", "patients", "courses", "receipt_counters", "hn_counters" }) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM commission_closings");
        jdbc.update("DELETE FROM treatment_fee_rules");
        jdbc.update("DELETE FROM staff WHERE id IN (SELECT id FROM app_users WHERE username NOT IN ('manager', 'therapist'))");
        jdbc.update("DELETE FROM app_users WHERE username NOT IN ('manager', 'therapist')");
        jdbc.update("DELETE FROM therapists WHERE code NOT IN ('RP-T01', 'RP-T02')");
        jdbc.update("DELETE FROM services WHERE code NOT IN ('PHYSIO_30', 'PHYSIO_60', 'ASSESSMENT')");
        jdbc.update("DELETE FROM branch WHERE branch_id NOT IN (SELECT branch_id FROM therapists WHERE code = 'RP-T01')");
        jdbc.update("UPDATE branch SET branch_code = '01', branch_status = TRUE WHERE branch_id IN (SELECT branch_id FROM therapists WHERE code = 'RP-T01')");
        jdbc.update("UPDATE therapists SET active = TRUE");
        jdbc.update("UPDATE services SET active = TRUE, price = 1200, default_duration_minutes = 60 WHERE code = 'PHYSIO_60'");
        jdbc.update("UPDATE services SET active = TRUE, price = 700, default_duration_minutes = 30 WHERE code = 'PHYSIO_30'");
        jdbc.update("UPDATE services SET active = TRUE, price = 1200, default_duration_minutes = 45 WHERE code = 'ASSESSMENT'");
        Course course = courses.save(new Course("CYPRESS", "คอร์สทดสอบ API", new BigDecimal("9000.00"), 5, 1, 60));
        return Map.of("courseId", course.getId(),
                "therapistId", jdbc.queryForObject("SELECT id FROM therapists WHERE code = 'RP-T01'", Long.class),
                "serviceId", jdbc.queryForObject("SELECT id FROM services WHERE code = 'PHYSIO_60'", Long.class),
                "tomorrow", LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).toString());
    }

    @GetMapping("/schema/branch-code")
    public Map<String, Object> branchCodeSchema() {
        return jdbc.queryForMap("""
                SELECT DATA_TYPE AS "dataType", CHARACTER_MAXIMUM_LENGTH AS "length"
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'BRANCH' AND COLUMN_NAME = 'BRANCH_CODE'
                """);
    }

    @PostMapping("/sales/{id}/report-date")
    @Transactional
    public void setReportDate(@PathVariable long id) {
        OffsetDateTime purchasedAt = OffsetDateTime.parse("2032-07-04T10:00:00+07:00");
        if (jdbc.update("UPDATE sales SET sold_at = ? WHERE id = ?", purchasedAt, id) != 1) {
            throw new IllegalArgumentException("Fixture sale does not exist");
        }
        jdbc.update("""
                UPDATE course_purchases SET purchased_at = ?, expires_at = ?
                WHERE id IN (SELECT course_purchase_id FROM sale_items WHERE sale_id = ?)
                """, purchasedAt, purchasedAt.plusDays(60), id);
    }
}
