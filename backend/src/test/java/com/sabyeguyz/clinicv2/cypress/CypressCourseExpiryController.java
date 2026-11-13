package com.sabyeguyz.clinicv2.cypress;

import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Fixture preparation only: not compiled into the production application jar. */
@Profile("cypress-v2")
@RestController
@RequestMapping("/__cypress/course-expiry")
@PreAuthorize("hasRole('MANAGER')")
@Transactional
public class CypressCourseExpiryController {
    private final JdbcTemplate jdbc;

    public CypressCourseExpiryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Expiry(Long courseId, LocalDate expiryDate) {}

    @PostMapping
    public void setExpiry(@RequestBody Expiry fixture) {
        if (fixture.courseId() == null || fixture.expiryDate() == null) {
            throw new IllegalArgumentException("courseId and expiryDate are required");
        }
        if (jdbc.update("UPDATE course SET expiry_date = ? WHERE course_id = ?",
                fixture.expiryDate(), fixture.courseId()) != 1) {
            throw new IllegalArgumentException("Fixture course not found");
        }
    }
}
