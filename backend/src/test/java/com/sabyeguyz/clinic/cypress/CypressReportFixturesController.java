package com.sabyeguyz.clinic.cypress;

import com.sabyeguyz.clinic.auth.AppUser;
import com.sabyeguyz.clinic.auth.AppUserRepository;
import com.sabyeguyz.clinic.auth.Role;
import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Report fixtures only; reports and every assertion run through Cypress HTTP tests. */
@Profile("cypress")
@RestController
@RequestMapping("/__cypress")
@PreAuthorize("hasRole('MANAGER')")
public class CypressReportFixturesController {
    private final CourseRepository courses;
    private final ServiceItemRepository services;
    private final BranchRepository branches;
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final JdbcTemplate jdbc;

    public CypressReportFixturesController(CourseRepository courses, ServiceItemRepository services,
            BranchRepository branches, AppUserRepository users, StaffRepository staff, JdbcTemplate jdbc) {
        this.courses = courses;
        this.services = services;
        this.branches = branches;
        this.users = users;
        this.staff = staff;
        this.jdbc = jdbc;
    }

    @PostMapping("/report-fixtures")
    @Transactional
    public Map<String, Long> fixtures() {
        Course course = courses.save(new Course("REPORT-CYPRESS", "คอร์สรายงาน",
                new BigDecimal("15000.00"), 10, 3, 90));
        return Map.of("courseId", course.getId(),
                "assessmentId", services.findByCode("ASSESSMENT").orElseThrow().getId());
    }

    @PostMapping("/report-branch")
    @Transactional
    public Map<String, String> branch() {
        Branch branch = branches.findByBranchCodeAndActiveTrue("02")
                .orElseGet(() -> branches.save(new Branch("02", "สาขาเชียงใหม่", "-", "-", null)));
        return Map.of("branchCode", branch.getBranchCode());
    }

    @PostMapping("/report-staff/{number}")
    @Transactional
    public Map<String, Long> salesperson(@PathVariable int number) {
        if (number < 1 || number > 2) throw new IllegalArgumentException("Fixture staff number must be 1 or 2");
        AppUser user = users.save(new AppUser("report-cypress-" + number, "hash", Role.THERAPIST));
        Staff person = staff.save(new Staff(user, "RSP-CYP-" + number, "ผู้ขาย ทดสอบ", "0800000030"));
        return Map.of("id", person.getId());
    }

    public record SaleDate(OffsetDateTime soldAt) {}

    @PostMapping("/report-sales/{id}/date")
    @Transactional
    public void date(@PathVariable long id, @RequestBody SaleDate date) {
        if (jdbc.update("UPDATE sales SET sold_at = ? WHERE id = ?", date.soldAt(), id) != 1) {
            throw new IllegalArgumentException("Fixture sale does not exist");
        }
        jdbc.update("""
                UPDATE course_purchases SET purchased_at = ?, expires_at = ?
                WHERE id IN (SELECT course_purchase_id FROM sale_items WHERE sale_id = ?)
                """, date.soldAt(), date.soldAt().plusDays(90), id);
    }
}
