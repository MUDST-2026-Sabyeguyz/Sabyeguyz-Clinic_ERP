package com.sabyeguyz.clinicv2.role;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ส่งรายการ permission พร้อม ID จริงให้ Manager เลือกตั้ง override */
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasRole('MANAGER')")
public class PermissionCatalogController {
    private final PermissionService permissions;

    public PermissionCatalogController(PermissionService permissions) {
        this.permissions = permissions;
    }

    public record PermissionOption(
            Long id,
            String permissionName,
            String moduleName) {}

    @GetMapping
    public List<PermissionOption> list() {
        return permissions.list().stream()
                .map(permission -> new PermissionOption(
                        permission.getId(),
                        permission.getPermissionName(),
                        permission.getModuleName()))
                .sorted(Comparator.comparing(PermissionOption::moduleName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(PermissionOption::permissionName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
