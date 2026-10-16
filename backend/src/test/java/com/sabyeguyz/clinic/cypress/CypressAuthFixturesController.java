package com.sabyeguyz.clinic.cypress;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Profile("cypress")
@RestController
@RequestMapping("/__cypress/auth")
@PreAuthorize("hasRole('MANAGER')")
public class CypressAuthFixturesController {
    private final JdbcTemplate jdbc;

    public CypressAuthFixturesController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/users/{id}")
    public Map<String, Object> storedUser(@PathVariable long id) {
        return jdbc.queryForMap("SELECT username, password_hash AS \"passwordHash\", role FROM app_users WHERE id = ?", id);
    }
}
