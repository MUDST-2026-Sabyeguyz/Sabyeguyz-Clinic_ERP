package com.sabyeguyz.clinicv2.cypress;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixture utilities for the v2 HTTP suite. They exist only in src/test, only under the cypress-v2
 * profile, and need a manager token. Reset empties every transactional table, keeps the seeded
 * reference data (users, branches, staff, services, commission scheme) and re-creates the
 * 9,000 THB test course (5 paid visits + 1 bonus, valid 60 days).
 */
@Profile("cypress-v2")
@RestController
@RequestMapping("/__cypress")
@PreAuthorize("hasRole('MANAGER')")
@Transactional
public class CypressV2FixturesController {
    private static final String TRANSACTIONAL_TABLES = String.join(", ",
            "refund_transaction", "transaction_cancellation", "payment_transaction", "course_usage_print_event",
            "course_usage_print", "commission_allocation_ledger", "course_ledger", "course_visit_usage",
            "course_transfer", "course_entitlement", "course_sale_staff_share", "course_shared_member",
            "commission_rate_override_audit", "monthly_commission_closing", "treatment", "sales_transaction",
            "course", "receipt", "receipt_sequence", "appointment_status_history", "appointment", "patient_visit", "hn_sequence",
            "patient", "audit_log");

    private final JdbcTemplate jdbc;
    private final com.sabyeguyz.clinicv2.calendar.FakeGoogleCalendarClient fakeCalendar;

    public CypressV2FixturesController(JdbcTemplate jdbc, com.sabyeguyz.clinicv2.calendar.FakeGoogleCalendarClient fakeCalendar) {
        this.jdbc = jdbc;
        this.fakeCalendar = fakeCalendar;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        jdbc.execute("TRUNCATE TABLE " + TRANSACTIONAL_TABLES + " RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM service WHERE service_code NOT IN ('PHYSIO_30', 'PHYSIO_60', 'COURSE_10', 'COURSE_5')");
        fakeCalendar.reset();
        jdbc.update("UPDATE branch SET google_calendar_id = NULL");
        resetReferenceData();
        jdbc.update("""
                INSERT INTO service (service_type, service_name, service_price, service_code, service_create_by,
                                     default_total_visits, default_bonus_visits, default_validity_value, default_validity_unit)
                SELECT 'Course'::service_type_enum, 'คอร์สทดสอบ API', 9000.00, 'CYPRESS', u.user_id, 5, 1, 60, 'Day'::validity_unit_enum
                FROM app_user u WHERE u.username = 'system'
                """);
        return Map.of(
                "courseId", id("SELECT service_id FROM service WHERE service_code = 'CYPRESS'"),
                "serviceId", id("SELECT service_id FROM service WHERE service_code = 'PHYSIO_60'"),
                "singleVisitServiceId", id("SELECT service_id FROM service WHERE service_code = 'PHYSIO_30'"),
                "therapistId", id("SELECT staff_id FROM staff WHERE staff_code = 'THP-001'"),
                "therapist2Id", id("SELECT staff_id FROM staff WHERE staff_code = 'THP-002'"),
                "managerStaffId", id("SELECT staff_id FROM staff WHERE staff_code = 'MGR-001'"),
                "tomorrow", LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).toString());
    }

    /** Removes what the reference-data specs created and switches everything back on. */
    private void resetReferenceData() {
        String seedUsers = "('system', 'manager', 'therapist', 'therapist2')";
        String seedBranches = "('01', '02')";
        jdbc.update("DELETE FROM user_role WHERE user_id IN (SELECT user_id FROM app_user WHERE username NOT IN " + seedUsers + ")");
        jdbc.update("DELETE FROM user_branch_access WHERE user_id IN (SELECT user_id FROM app_user WHERE username NOT IN " + seedUsers + ")");
        jdbc.update("DELETE FROM app_user WHERE username NOT IN " + seedUsers);
        jdbc.update("DELETE FROM staff WHERE staff_code NOT IN ('MGR-001', 'THP-001', 'THP-002')");
        jdbc.update("DELETE FROM user_branch_access WHERE branch_id IN (SELECT branch_id FROM branch WHERE branch_code NOT IN " + seedBranches + ")");
        jdbc.update("DELETE FROM room WHERE room_name NOT LIKE 'ห้องกายภาพ%' OR branch_id IN (SELECT branch_id FROM branch WHERE branch_code NOT IN " + seedBranches + ")");
        jdbc.update("DELETE FROM branch WHERE branch_code NOT IN " + seedBranches);
        jdbc.update("DELETE FROM payment_method WHERE method_name NOT IN ('Cash', 'BankTransfer', 'PromptPay', 'CreditCard')");
        jdbc.update("DELETE FROM master_data_value WHERE value_name NOT IN ('General', 'VIP', 'Walk-in', 'Facebook', 'Friend', 'None')");
        jdbc.update("DELETE FROM master_data_type WHERE type_name NOT IN ('Customer Group', 'Referral Channel', 'Insurance Type', 'Insurance Company')");
        jdbc.update("DELETE FROM commission_tier WHERE commission_scheme_id <> (SELECT MIN(commission_scheme_id) FROM commission_scheme)");
        jdbc.update("DELETE FROM commission_scheme WHERE commission_scheme_id <> (SELECT MIN(commission_scheme_id) FROM commission_scheme)");
        jdbc.update("UPDATE commission_tier SET tier_status = TRUE");
        jdbc.update("UPDATE staff SET staff_status = TRUE, termination_date = NULL, commission_after_termination_policy = NULL, termination_policy_locked_at = NULL");
        jdbc.update("UPDATE app_user SET user_status = TRUE WHERE username <> 'system'");
        jdbc.update("UPDATE branch SET branch_status = TRUE");
        jdbc.update("UPDATE payment_method SET payment_method_status = TRUE");
        jdbc.update("UPDATE master_data_value SET value_status = TRUE");
        jdbc.update("UPDATE room SET room_status = TRUE");
        jdbc.update("UPDATE service SET service_status = TRUE");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
