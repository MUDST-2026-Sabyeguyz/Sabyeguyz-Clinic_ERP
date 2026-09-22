package com.sabyeguyz.clinic.databasev2.audit;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@Profile("database-v2")
public class DatabaseV2AuditController {

    private final DatabaseV2AuditRepository auditRepository;

    public DatabaseV2AuditController(DatabaseV2AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    public Page<DatabaseV2AuditLogDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Invalid page or size");
        }
        return auditRepository.list(page, size);
    }
}
