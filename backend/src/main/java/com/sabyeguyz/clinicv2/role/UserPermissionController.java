package com.sabyeguyz.clinicv2.role;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** API สำหรับอ่าน ตั้งค่า และลบ permission override ของผู้ใช้ */
@RestController
@RequestMapping("/api/users/{userId}/permissions")
@Transactional
@PreAuthorize("hasRole('MANAGER')")
public class UserPermissionController {
    private final UserPermissionService service;

    public UserPermissionController(UserPermissionService service) {
        this.service = service;
    }

    public record Request(@NotNull Boolean allowed) {}

    public record Response(
            Long permissionId,
            String permissionName,
            boolean allowed,
            Long updatedBy,
            LocalDateTime updatedAt) {}

    @GetMapping
    public List<Response> list(@PathVariable Long userId) {
        return service.list(userId)
                .stream()
                .map(UserPermissionController::toResponse)
                .toList();
    }

    @PutMapping("/{permissionId}")
    public Response setAllowed(
            @PathVariable Long userId,
            @PathVariable Long permissionId,
            @Valid @RequestBody Request request) {
        return toResponse(service.setAllowed(userId, permissionId, request.allowed()));
    }

    @DeleteMapping("/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(
            @PathVariable Long userId,
            @PathVariable Long permissionId) {
        service.reset(userId, permissionId);
    }

    private static Response toResponse(UserPermission override) {
        return new Response(
                override.getPermission().getId(),
                override.getPermission().getPermissionName(),
                override.isAllowed(),
                override.getUpdatedBy().getId(),
                override.getUpdatedAt());
    }
}
