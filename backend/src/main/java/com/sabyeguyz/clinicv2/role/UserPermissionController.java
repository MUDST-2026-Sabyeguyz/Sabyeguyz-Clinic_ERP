package com.sabyeguyz.clinicv2.role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
<<<<<<< HEAD
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/permissions")
@Transactional
public class UserPermissionController {
    private final UserPermissionService service;

    public UserPermissionController(UserPermissionService service) {
        this.service = service;
    }

    public record Request(@NotNull Boolean allowed) {}
    public record Response(Long permissionId, String permissionName, boolean allowed, Long updatedBy, LocalDateTime updatedAt) {}

    @GetMapping
    public List<Response> list(@PathVariable Long userId) {
        return service.list(userId).stream()
                .map(UserPermissionController::toResponse)
                .toList();
    }

    @PutMapping("/{permissionId}")
    public Response setAllowed(
            @PathVariable Long userId,
            @PathVariable Long permissionId,
            @Valid @RequestBody Request request) {
        return toResponse(service.setAllowed(
                userId, permissionId, request.allowed()));
=======
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

    public UserPermissionController(UserPermissionService service) { this.service = service; }

    public record Request(@NotNull Boolean allowed) {}
    public record Response(Long permissionId, String permissionName, boolean allowed,
                           Long updatedBy, LocalDateTime updatedAt) {}

    @GetMapping
    public List<Response> list(@PathVariable Long userId) {
        return service.list(userId).stream().map(UserPermissionController::toResponse).toList();
    }

    @PutMapping("/{permissionId}")
    public Response setAllowed(@PathVariable Long userId, @PathVariable Long permissionId,
                               @Valid @RequestBody Request request) {
        return toResponse(service.setAllowed(userId, permissionId, request.allowed()));
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
    }

    @DeleteMapping("/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
<<<<<<< HEAD
    public void reset(
            @PathVariable Long userId,
            @PathVariable Long permissionId) {
=======
    public void reset(@PathVariable Long userId, @PathVariable Long permissionId) {
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
        service.reset(userId, permissionId);
    }

    private static Response toResponse(UserPermission override) {
<<<<<<< HEAD
        return new Response(
                override.getPermission().getId(),
                override.getPermission().getPermissionName(),
                override.isAllowed(),
                override.getUpdatedBy().getId(),
                override.getUpdatedAt());
    }
}
=======
        return new Response(override.getPermission().getId(), override.getPermission().getPermissionName(),
                override.isAllowed(), override.getUpdatedBy().getId(), override.getUpdatedAt());
    }
}
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
