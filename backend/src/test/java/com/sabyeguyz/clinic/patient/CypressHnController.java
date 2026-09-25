package com.sabyeguyz.clinic.patient;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Exposes package-private domain operations to Cypress; all assertions live in JavaScript. */
@Profile("cypress")
@RestController
@RequestMapping("/__cypress/hn")
@PreAuthorize("hasRole('MANAGER')")
public class CypressHnController {
    private final HnGenerator generator;
    private final JdbcTemplate jdbc;

    public CypressHnController(HnGenerator generator, JdbcTemplate jdbc) {
        this.generator = generator;
        this.jdbc = jdbc;
    }

    public record FormatRequest(LocalDate date, String branchCode, long runningNumber) {}
    public record AssignRequest(String first, String second) {}

    @PostMapping("/format")
    public Map<String, String> format(@RequestBody FormatRequest request) {
        return Map.of("hn", HnGenerator.format(request.date(), request.branchCode(), request.runningNumber()));
    }

    @PostMapping("/next")
    public Map<String, String> next(@RequestBody FormatRequest request) {
        return Map.of("hn", generator.next(1L, request.branchCode(), request.date()));
    }

    @PostMapping("/assign")
    public Map<String, String> assign(@RequestBody AssignRequest request) {
        Patient patient = new Patient();
        patient.assignHn(request.first());
        patient.assignHn(request.second());
        return Map.of("hn", patient.getHn());
    }

    @PostMapping("/branch-code")
    public void numericBranchCode(@RequestBody Map<String, String> request) {
        jdbc.update("UPDATE branches SET numeric_code = ? WHERE code = 'RP'", request.get("numericCode"));
    }
}
