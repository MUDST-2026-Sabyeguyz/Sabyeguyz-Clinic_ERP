package com.sabyeguyz.clinicv2.role;

import java.time.Instant;
import java.util.List;
import com.sabyeguyz.clinicv2.auth.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class PermissionService {
    private final PermissionRepository repository;
    private final EffectivePermissionService effectivePermissions;
    private final UserRoleRepository userRoles;

    public PermissionService(
            PermissionRepository repository,
            EffectivePermissionService effectivePermissions,
            UserRoleRepository userRoles) {
        this.repository = repository;
        this.effectivePermissions = effectivePermissions;
        this.userRoles = userRoles;
    }

    @Transactional(readOnly = true)
    public List<Permission> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Permission requireAllowed(String permissionName, Long actorId) {
        Permission permission = repository
                .findByPermissionName(permissionName)
                .orElseThrow(() -> new AccessDeniedException("ไม่พบสิทธิ์ที่อนุญาต"));

        effectivePermissions.requireAllowed(
                actorId, permission.getId());

        return permission;
    }

    @Transactional(readOnly = true)
    public Permission requireManagerAllowed(
            String permissionName, Long actorId) {
        boolean manager = actorId != null
                && userRoles.findByUserId(actorId).stream()
                .anyMatch(link -> "Manager".equalsIgnoreCase(
                        link.getRole().getRoleName()));
        if (!manager) {
            throw new AccessDeniedException("เฉพาะ Manager เท่านั้นที่ทำรายการนี้ได้");
        }

        return requireAllowed(permissionName, actorId);
    }

    /** The named permission, created on first use (cancellations must cite the permission that allowed them). */
    @Transactional
    public Permission getOrCreate(String permissionName, String moduleName, Long actorId) {
        return repository.findByPermissionName(permissionName)
                .orElseGet(() -> create(permissionName, moduleName, actorId));
    }

    @Transactional
    public Permission create(String permissionName, String moduleName, Long actorId) {
        if (permissionName == null || permissionName.isBlank()) {
            throw new IllegalArgumentException("permissionName ห้ามว่าง");
        }
        Permission permission = new Permission();
        permission.setPermissionName(permissionName.trim());
        permission.setModuleName(moduleName == null ? null : moduleName.trim());
        permission.setPermissionCreateAt(Instant.now());
        permission.setPermissionCreateBy(actorId);
        return repository.save(permission);
    }
}
