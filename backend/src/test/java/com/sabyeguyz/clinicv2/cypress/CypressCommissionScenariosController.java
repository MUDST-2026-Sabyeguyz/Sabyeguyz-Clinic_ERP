package com.sabyeguyz.clinicv2.cypress;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Test-only helpers for the commission acceptance specs: time travel that the public API does not offer (a course sold in
 * an earlier month) and plain reads of the ledgers.
 */
@Profile("cypress-v2")
@RestController
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/__cypress/commission")
public class CypressCommissionScenariosController {
    private final JdbcTemplate jdbc;

    public CypressCommissionScenariosController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Moves the course (as if it was sold {@code months} months ago) so that an earlier month can be closed for it. */
    @PostMapping("/courses/{courseId}/shift-sale-month")
    public Map<String, Object> shiftSaleMonth(@PathVariable Long courseId, @RequestParam(defaultValue = "1") int months) {
        String current = jdbc.queryForObject("SELECT sale_month FROM course WHERE course_id = ?", String.class, courseId);
        String shifted = YearMonth.parse(current).minusMonths(months).toString();
        jdbc.update("UPDATE course SET sale_month = ? WHERE course_id = ?", shifted, courseId);
        return Map.of("saleMonth", shifted);
    }

    /** Fees earned by single visits, oldest first. */
    @GetMapping("/single-visit-fees")
    public List<Map<String, Object>> singleVisitFees() {
        return jdbc.queryForList("SELECT fee_id, visit_id, staff_id, fee_type::text AS fee_type, fee_value, calculation_base, "
                + "fee_amount, entry_type::text AS entry_type, fee_version, reverses_fee_id FROM single_visit_fee_ledger ORDER BY fee_id");
    }

    /** Allocation rows of a course, oldest first. */
    @GetMapping("/courses/{courseId}/allocations")
    public List<Map<String, Object>> allocations(@PathVariable Long courseId) {
        return jdbc.queryForList("SELECT a.allocation_id, u.usage_sequence, a.entry_type::text AS entry_type, a.allocation_version, "
                + "a.owner_staff_id, a.treating_staff_id, a.owner_net_commission, a.treatment_fee_amount, "
                + "a.treatment_fee_calculation_base, a.reverses_allocation_id "
                + "FROM commission_allocation_ledger a JOIN course_visit_usage u ON u.visit_usage_id = a.visit_usage_id "
                + "WHERE a.course_id = ? ORDER BY a.allocation_id", courseId);
    }

    /** The sessions of a course in order of position, with what the Owner earned (null while unknown). */
    @GetMapping("/courses/{courseId}/usages")
    public List<Map<String, Object>> usages(@PathVariable Long courseId) {
        return jdbc.queryForList(
                "SELECT visit_usage_id, visit_id, visit_date::text AS visit_date, "
                        + "usage_sequence, usage_status::text AS usage_status, treating_staff_id, "
                        + "commission_allocation_per_visit, owner_net_commission, "
                        + "substitute_treatment_fee_amount, replaces_usage_id, treatment_id "
                        + "FROM course_visit_usage WHERE course_id = ? "
                        + "ORDER BY usage_sequence, visit_usage_id",
                courseId);
    }

    /** Usage slips of a course (document snapshots), oldest first. */
    @GetMapping("/courses/{courseId}/prints")
    public List<Map<String, Object>> prints(@PathVariable Long courseId) {
        return jdbc.queryForList("SELECT p.print_id, u.usage_sequence, p.document_version, p.document_snapshot::text AS snapshot "
                + "FROM course_usage_print p JOIN course_visit_usage u ON u.visit_usage_id = p.visit_usage_id "
                + "WHERE p.course_id = ? ORDER BY p.print_id", courseId);
    }

    /** The cached money totals of a course. */
    @GetMapping("/courses/{courseId}/totals")
    public Map<String, Object> totals(@PathVariable Long courseId) {
        return jdbc.queryForMap("SELECT commission_status::text AS commission_status, course_status::text AS course_status, "
                + "total_course_commission_pool, commission_allocation_per_visit, owner_net_commission_released_total, "
                + "substitute_treatment_fee_total, commission_outstanding, remaining_visits, visits_used "
                + "FROM course WHERE course_id = ?", courseId);
    }
}
