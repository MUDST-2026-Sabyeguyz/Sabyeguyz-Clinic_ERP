package com.sabyeguyz.clinicv2.audit;

import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Matches frontend/src/api/client.ts's auditApi (GET /api/audit-logs). */
@Transactional
@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record Response(Long id, String tableName, String recordId, String action, String oldValue,
                            String newValue, Long changedBy, Instant changedAt) {}
    public record PageResponse(List<Response> items, int page, int size, long totalItems, int totalPages) {}

    @GetMapping
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> result = repository.findAll(PageRequest.of(page, size, Sort.by("id").descending()));
        return new PageResponse(result.getContent().stream().map(AuditController::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static Response toResponse(AuditLog log) {
        return new Response(log.getId(), log.getTableName(), log.getRecordId(), log.getAction().name(),
                log.getOldValue(), log.getNewValue(), log.getChangedBy(), log.getChangedAt());
    }
}
