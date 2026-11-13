package com.sabyeguyz.clinicv2.catalog;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's courseApi's template CRUD
 * (GET/POST/PUT/DELETE /api/courses) — these are Course templates (v2
 * Service rows with serviceType = Course), NOT sold Course instances. A
 * sold instance is v2's own Course entity, exposed through
 * {@link com.sabyeguyz.clinicv2.sale.SalesTransactionController}'s
 * course-purchase endpoints instead.
 */
@Transactional
@RestController
@RequestMapping("/api/courses")
public class CourseTemplateController {
    private final ServiceCatalogService service;
    private final ServiceRepository repository;
    private final CurrentActor actor;
    private final AuditLogService audit;
    private final ObjectMapper json;

    public CourseTemplateController(ServiceCatalogService service, ServiceRepository repository, CurrentActor actor,
                                    AuditLogService audit, ObjectMapper json) {
        this.service = service;
        this.repository = repository;
        this.actor = actor;
        this.audit = audit;
        this.json = json;
    }

    public record Request(@NotBlank String code, @NotBlank String name, @NotNull BigDecimal price,
                           @NotNull Integer sessions, Integer bonusSessions, Integer validityDays,
                           Integer validityValue, String validityUnit, Integer defaultDurationMinutes) {
        /** Older clients: validity in days only. */
        public Request(String code, String name, BigDecimal price, Integer sessions, Integer bonusSessions,
                       Integer validityDays) {
            this(code, name, price, sessions, bonusSessions, validityDays, null, null, null);
        }

        Integer duration() {
            if (defaultDurationMinutes == null) return null;
            if (defaultDurationMinutes < 1) {
                throw new IllegalArgumentException("ระยะเวลานัดต้องเป็นจำนวนเต็มอย่างน้อย 1 นาที");
            }
            return defaultDurationMinutes;
        }

        int validity() {
            Integer value = validityValue != null ? validityValue : validityDays;
            if (value == null || value < 1) throw new IllegalArgumentException("คอร์สต้องระบุอายุคอร์สอย่างน้อย 1");
            return value;
        }

        com.sabyeguyz.clinicv2.enums.ValidityUnit unit() {
            if (validityUnit == null || validityUnit.isBlank()) return com.sabyeguyz.clinicv2.enums.ValidityUnit.Day;
            for (com.sabyeguyz.clinicv2.enums.ValidityUnit unit : com.sabyeguyz.clinicv2.enums.ValidityUnit.values()) {
                if (unit.name().equalsIgnoreCase(validityUnit.trim())) return unit;
            }
            throw new IllegalArgumentException("หน่วยอายุคอร์สต้องเป็น Day, Month หรือ Year");
        }
    }

    public record Response(Long id, String code, String name, BigDecimal price, int sessions, int bonusSessions,
                            int validityDays, boolean active, int validityValue, String validityUnit,
                            int defaultDurationMinutes) {}

    @GetMapping
    public List<Response> list() {
        return repository.findByServiceTypeOrderByServiceName(ServiceType.Course).stream()
                .map(CourseTemplateController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response create(@Valid @RequestBody Request request) {
        Service created = service.create(request.code(), request.name(), ServiceType.Course, request.price(),
                request.sessions(), request.bonusSessions() == null ? 0 : request.bonusSessions(),
                request.validity(), actor.currentId());
        created.setDefaultValidityValue(request.validity());
        created.setDefaultValidityUnit(request.unit());
        created.setDefaultAppointmentMinutes(request.duration());
        return toResponse(repository.save(created));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Service existing = find(id);
        Response before = toResponse(existing);
        boolean changed = !Objects.equals(existing.getServiceName(), request.name())
                || existing.getServicePrice().compareTo(request.price()) != 0
                || !Objects.equals(existing.getDefaultTotalVisits(), request.sessions())
                || !Objects.equals(existing.getDefaultBonusVisits(), 0)
                || !Objects.equals(existing.getDefaultValidityValue(), request.validity())
                || existing.getDefaultValidityUnit() != request.unit();
        existing.setServiceName(request.name());
        existing.setServicePrice(request.price());
        existing.setDefaultTotalVisits(request.sessions());
        existing.setDefaultBonusVisits(0);
        existing.setDefaultValidityValue(request.validity());
        existing.setDefaultValidityUnit(request.unit());
        existing.setDefaultAppointmentMinutes(request.duration());
        Response after = toResponse(repository.save(existing));
        if (changed) audit.record("course_template", String.valueOf(id), AuditAction.UPDATE,
                json.valueToTree(before).toString(), json.valueToTree(after).toString(), actor.currentId());
        return after;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id, actor.currentId());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/activate")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response activate(@PathVariable Long id) {
        return toResponse(service.activate(id, actor.currentId()));
    }

    private Service find(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
    }

    private static Response toResponse(Service s) {
        return new Response(s.getId(), s.getServiceCode(), s.getServiceName(), s.getServicePrice(),
                s.getDefaultTotalVisits() == null ? 0 : s.getDefaultTotalVisits(),
                0, // Sessions already includes promotional sessions.
                s.getDefaultValidityDays() == null ? 0 : s.getDefaultValidityDays(), s.isServiceStatus(),
                s.getDefaultValidityValue() == null ? 0 : s.getDefaultValidityValue(),
                s.getDefaultValidityUnit() == null ? "Day" : s.getDefaultValidityUnit().name(),
                s.getDefaultAppointmentMinutes() == null ? 0 : s.getDefaultAppointmentMinutes());
    }
}
