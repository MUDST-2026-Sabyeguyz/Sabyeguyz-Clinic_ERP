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
            "adjustment_application", "staff_pay_adjustment", "single_visit_fee_ledger", "staff_monthly_payment",
            "course_refund_item", "refund_transaction", "transaction_cancellation", "payment_transaction", "course_usage_print_event",
            "course_usage_print", "commission_allocation_ledger", "course_ledger", "course_visit_usage",
            "course_transfer", "course_entitlement", "course_sale_staff_share", "course_shared_member",
            "commission_rate_override_audit", "monthly_commission_closing",
            "branch_monthly_closing", "treatment", "sales_transaction",
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
        jdbc.update("UPDATE branch SET branch_email = NULL, email = NULL, license_no = NULL, "
                + "booking_open_time = NULL, opening_time = NULL, booking_close_time = NULL, "
                + "closing_time = NULL, default_appointment_minutes = NULL, google_connection_id = NULL, "
                + "calendar_sync_status = NULL, calendar_last_error = NULL, calendar_last_synced_at = NULL");
        jdbc.update("UPDATE service SET default_appointment_minutes = NULL, default_duration_minutes = NULL");
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
        // the seed scheme as the demo data defines it (tests change its name, dates and tiers)
        jdbc.update("UPDATE commission_scheme SET scheme_name = 'Default Tier Scheme', scheme_version = 1, "
                + "effective_from = DATE '2026-01-01', effective_to = NULL");
        jdbc.update("DELETE FROM commission_tier");
        jdbc.update("INSERT INTO commission_tier (commission_scheme_id, permission_id, tier_order, minimum_monthly_sales, "
                + "maximum_monthly_sales, commission_rate, effective_from, tier_create_by) "
                + "SELECT cs.commission_scheme_id, cs.permission_id, t.tier_order, t.lo, t.hi, t.rate, cs.effective_from, "
                + "cs.scheme_create_by FROM commission_scheme cs CROSS JOIN (VALUES "
                + "(1, 0.00, 59999.99, 0.05), (2, 60000.00, 69999.99, 0.06), (3, 70000.00, 79999.99, 0.07), "
                + "(4, 80000.00, 89999.99, 0.08), (5, 90000.00, NULL::numeric, 0.10)) AS t(tier_order, lo, hi, rate)");
        jdbc.update("DELETE FROM treatment_fee_rule WHERE treatment_fee_rule_id <> (SELECT MIN(treatment_fee_rule_id) FROM treatment_fee_rule)");
        jdbc.update("UPDATE treatment_fee_rule SET fee_type = 'Fixed'::fee_type_enum, "
                + "fee_value = 45, effective_from = DATE '2026-01-01', "
                + "effective_to = NULL");
        jdbc.update("UPDATE staff SET staff_status = TRUE, termination_date = NULL");
        jdbc.update("UPDATE app_user SET user_status = TRUE WHERE username <> 'system'");
        jdbc.update("UPDATE branch SET branch_status = TRUE");
        jdbc.update("UPDATE payment_method SET payment_method_status = " + "(method_name::text IN ('CreditCard', 'PromptPay', 'BankTransfer'))");
        jdbc.update("UPDATE master_data_value SET value_status = TRUE");
        jdbc.update("UPDATE room SET room_status = TRUE");
        jdbc.update("UPDATE service SET service_status = TRUE");
    }

    public record FeeFixtureRequest(
            String feeType,
            java.math.BigDecimal feeValue) {}

    @PostMapping("/treatment-fee-fixture")
    public Map<String, Object> treatmentFeeFixture(
            @org.springframework.web.bind.annotation.RequestBody
            FeeFixtureRequest request) {

        if ((! "Fixed".equals(request.feeType())
                && ! "Percentage".equals(request.feeType()))
                || request.feeValue() == null
                || request.feeValue().signum() < 0
                || request.feeValue().scale() > 2
                || ("Percentage".equals(request.feeType())
                && request.feeValue().compareTo(
                new java.math.BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("ข้อมูลค่ามือทดสอบไม่ถูกต้อง");
        }

        if (id("SELECT COUNT(*) FROM course_visit_usage") > 0
                || id("SELECT COUNT(*) FROM single_visit_fee_ledger") > 0
                || id("SELECT COUNT(*) FROM commission_allocation_ledger") > 0
                || id("SELECT COUNT(*) FROM treatment_fee_rule") != 1) {
            throw new IllegalStateException(
                    "ต้องเตรียม fixture ก่อนใช้สิทธิ์หรือคำนวณค่ามือ");
        }

        int updated = jdbc.update(
                "UPDATE treatment_fee_rule "
                        + "SET fee_type = ?::fee_type_enum, fee_value = ?",
                request.feeType(),
                request.feeValue());

        return Map.of("updated", updated);
    }

    public record FeeAvailabilityFixtureRequest(Boolean available) {}

    @PostMapping("/treatment-fee-availability-fixture")
    public Map<String, Object> treatmentFeeAvailabilityFixture(
            @org.springframework.web.bind.annotation.RequestBody
            FeeAvailabilityFixtureRequest request) {

        if (request.available() == null) {
            throw new IllegalArgumentException("ต้องระบุ available");
        }

        if (id("SELECT COUNT(*) FROM course_visit_usage") > 0
                || id("SELECT COUNT(*) FROM single_visit_fee_ledger") > 0
                || id("SELECT COUNT(*) FROM commission_allocation_ledger") > 0
                || id("SELECT COUNT(*) FROM treatment_fee_rule") != 1) {
            throw new IllegalStateException(
                    "ต้องเตรียม fixture ก่อนใช้สิทธิ์หรือคำนวณค่ามือ");
        }

        LocalDate start =
                com.sabyeguyz.clinicv2.config.ClinicTime.today()
                        .withDayOfMonth(1);

        if (!request.available()) {
            start = start.plusMonths(1);
        }

        int updated = jdbc.update(
                "UPDATE treatment_fee_rule "
                        + "SET effective_from = ?, effective_to = NULL",
                java.sql.Date.valueOf(start));

        return Map.of("updated", updated);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
