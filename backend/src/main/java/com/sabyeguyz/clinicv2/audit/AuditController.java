package com.sabyeguyz.clinicv2.audit;

import com.sabyeguyz.clinicv2.auth.AppUser;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matches frontend/src/api/client.ts's auditApi (GET /api/audit-logs). Only a Manager reads the trail. The list can be
 * narrowed by table, record, action, user and by day (clinic time, both days included).
 */
@Transactional
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('MANAGER')")
public class AuditController {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;
    private final AppUserRepository users;

    public AuditController(AuditLogRepository repository, AppUserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    /** changedByName is empty for an automatic event: its origin is the "source" inside newValue. */
    public record Response(Long id, String tableName, String recordId, String action, String oldValue,
                            String newValue, Long changedBy, Instant changedAt, String changedByName) {}
    public record PageResponse(List<Response> items, int page, int size, long totalItems, int totalPages) {}

    @GetMapping
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
                              @RequestParam(required = false) String tableName,
                              @RequestParam(required = false) String recordId,
                              @RequestParam(required = false) String action,
                              @RequestParam(required = false) Long changedBy,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("วันที่สิ้นสุดต้องไม่อยู่ก่อนวันที่เริ่ม");
        }
        AuditAction parsedAction = parseAction(action);
        Specification<AuditLog> filter = (root, query, builder) -> {
            List<Predicate> all = new ArrayList<>();
            if (tableName != null && !tableName.isBlank()) all.add(builder.equal(root.get("tableName"), tableName.trim()));
            if (recordId != null && !recordId.isBlank()) all.add(builder.equal(root.get("recordId"), recordId.trim()));
            if (parsedAction != null) all.add(builder.equal(root.get("action"), parsedAction));
            if (changedBy != null) all.add(builder.equal(root.get("changedBy"), changedBy));
            if (from != null) {
                all.add(builder.greaterThanOrEqualTo(root.<Instant>get("changedAt"), from.atStartOfDay(CLINIC_ZONE).toInstant()));
            }
            if (to != null) {
                all.add(builder.lessThan(root.<Instant>get("changedAt"), to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant()));
            }
            return builder.and(all.toArray(new Predicate[0]));
        };
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<AuditLog> result = repository.findAll(filter,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("changedAt").descending().and(Sort.by("id").descending())));
        Map<Long, String> names = namesOf(result.getContent());
        return new PageResponse(result.getContent().stream().map(log -> toResponse(log, names)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** The tables that have audit rows (for the filter). */
    @GetMapping("/tables")
    public List<String> tables() {
        return repository.findDistinctTableNames();
    }

    private static AuditAction parseAction(String action) {
        if (action == null || action.isBlank()) return null;
        try {
            return AuditAction.valueOf(action.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("action ต้องเป็น INSERT, UPDATE หรือ DELETE");
        }
    }

    /** Staff name of each user who made a change (the username when the account has no staff record). */
    private Map<Long, String> namesOf(List<AuditLog> logs) {
        List<Long> ids = logs.stream().map(AuditLog::getChangedBy).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (AppUser user : users.findAllById(ids)) {
            names.put(user.getId(), user.getStaff() != null
                    ? user.getStaff().getStaffFname() + " " + user.getStaff().getStaffLname()
                    : user.getUsername());
        }
        return names;
    }

    private static Response toResponse(AuditLog log, Map<Long, String> names) {
        return new Response(log.getId(), log.getTableName(), log.getRecordId(), log.getAction().name(),
                log.getOldValue(), log.getNewValue(), log.getChangedBy(), log.getChangedAt(),
                log.getChangedBy() == null ? null : names.get(log.getChangedBy()));
    }
}
