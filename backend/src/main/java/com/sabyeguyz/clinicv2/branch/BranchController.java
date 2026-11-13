package com.sabyeguyz.clinicv2.branch;

import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.sabyeguyz.clinicv2.auth.CurrentActor;

/**
 * Matches frontend/src/api/client.ts's branchApi (GET/POST/PUT/DELETE
 * /api/branches). Response shape is intentionally NOT backward compatible
 * with the old {@code {id, code, numericCode, name, active}} — v2's Branch
 * data dictionary merges code+numericCode into one branchCode and adds
 * address/phone. The frontend's Branch type and branchApi calls need
 * updating to match this shape.
 */
@Transactional
@RestController
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchService service;
    private final CurrentActor actor;
    private final AuditLogService audit;

    public BranchController(BranchService service, CurrentActor actor, AuditLogService audit) {
        this.service = service;
        this.actor = actor;
        this.audit = audit;
    }

    public record Request(
            @Pattern(regexp = "\\d{2}", message = "branchCode ต้องเป็นตัวเลข 2 หลัก") String branchCode, // optional on create: auto-generated
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 255) String email,
            @Size(max = 50) String licenseNo,
            @Size(max = 5) String bookingOpenTime,
            @Size(max = 5) String bookingCloseTime,
            Integer defaultAppointmentMinutes) {
        /** Old clients send only the four required fields; the optional settings are then left empty. */
        public Request(
                String branchCode,
                String name,
                String address,
                String phone) {
            this(
                    branchCode,
                    name,
                    address,
                    phone,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        BranchService.Settings settings() {
            return new BranchService.Settings(
                    email,
                    licenseNo,
                    bookingOpenTime,
                    bookingCloseTime,
                    defaultAppointmentMinutes);
        }
    }

    public record Response(
            Long id,
            String branchCode,
            String name,
            String address,
            String phone,
            boolean active,
            String email,
            String licenseNo,
            String bookingOpenTime,
            String bookingCloseTime,
            Integer defaultAppointmentMinutes) {}

    @GetMapping
    public List<Response> list() {
        return service.list().stream().map(BranchController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response create(@Valid @RequestBody Request request) {
        String code = request.branchCode() == null || request.branchCode().isBlank()
                ? service.nextBranchCode()
                : request.branchCode();
        Branch branch = service.create(
                code,
                request.name(),
                request.address(),
                request.phone(),
                actor.currentId());
        Branch saved = service.applySettings(branch.getId(), request.settings(), actor.currentId());
        audit.recordChange("branch", String.valueOf(saved.getId()), AuditAction.INSERT, null, snapshot(saved),
                actor.currentId());
        return toResponse(saved);
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response update(
            @PathVariable Long id,
            @Valid @RequestBody Request request) {
        Branch existing = service.get(id);

        if (request.branchCode() != null
                && !existing.getBranchCode().equals(request.branchCode().trim())) {
            throw new IllegalArgumentException("ไม่อนุญาตให้แก้ branchCode เพื่อรักษา HN เดิม");
        }
        Map<String, Object> before = snapshot(existing);
        service.update(id, request.name(), request.address(), request.phone(), actor.currentId());
        Branch saved = service.applySettings(id, request.settings(), actor.currentId());
        audit.recordChange("branch", String.valueOf(id), AuditAction.UPDATE, before, snapshot(saved), actor.currentId());
        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public void deactivate(@PathVariable Long id) {
        Map<String, Object> before = snapshot(service.get(id));
        Branch saved = service.deactivate(id, actor.currentId());
        audit.recordChange("branch", String.valueOf(id), AuditAction.UPDATE, before, snapshot(saved), actor.currentId());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/activate")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response activate(@PathVariable Long id) {
        Map<String, Object> before = snapshot(service.get(id));
        Branch saved = service.activate(id, actor.currentId());
        audit.recordChange("branch", String.valueOf(id), AuditAction.UPDATE, before, snapshot(saved), actor.currentId());
        return toResponse(saved);
    }

    /** What the audit trail keeps of a branch: the values a manager can see and change on the settings page. */
    private static Map<String, Object> snapshot(Branch branch) {
        return AuditLogService.fields(
                "branch_code", branch.getBranchCode(),
                "branch_name", branch.getBranchName(),
                "address", branch.getAddress(),
                "phone", branch.getPhone(),
                "email", branch.getBranchEmail(),
                "license_no", branch.getLicenseNo(),
                "booking_open_time", hhmm(branch.getBookingOpenTime()),
                "booking_close_time", hhmm(branch.getBookingCloseTime()),
                "default_appointment_minutes", branch.getDefaultAppointmentMinutes(),
                "branch_status", branch.isBranchStatus());
    }

    private static Response toResponse(Branch branch) {
        return new Response(
                branch.getId(),
                branch.getBranchCode(),
                branch.getBranchName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.isBranchStatus(),
                branch.getBranchEmail(),
                branch.getLicenseNo(),
                hhmm(branch.getBookingOpenTime()),
                hhmm(branch.getBookingCloseTime()),
                branch.getDefaultAppointmentMinutes());
    }

    private static String hhmm(java.time.LocalTime time) {
        return time == null ? null : time.toString().substring(0, 5);
    }
}
