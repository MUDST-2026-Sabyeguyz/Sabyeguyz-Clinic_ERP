package com.sabyeguyz.clinic.audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@Profile("!database-v2")
public class AuditController {
    private final AuditLogRepository logs;
    public AuditController(AuditLogRepository logs) { this.logs = logs; }
    @GetMapping
    public Page<AuditLog> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("Invalid page or size");
        return logs.findAll(PageRequest.of(page, size, Sort.by("id").descending()));
    }
}
