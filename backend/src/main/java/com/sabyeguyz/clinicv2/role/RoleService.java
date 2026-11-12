package com.sabyeguyz.clinicv2.role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;

    public RoleService(
            RoleRepository roles,
            PermissionRepository permissions,
            RolePermissionRepository rolePermissions) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public List<Role> list() {
        return roles.findAll();
    }

    @Transactional
    public Role create(
            String roleName,
            Long actorId) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("roleName ห้ามว่าง");
        }

        if (roles.findByRoleName(roleName.trim()).isPresent()) {
            throw new IllegalArgumentException("ชื่อบทบาทซ้ำ");
        }

        Role role = new Role();
        role.setRoleName(roleName.trim());
        role.setRoleCreateAt(Instant.now());
        role.setRoleCreateBy(actorId);

        return roles.save(role);
    }

    /** Grants a Permission to a Role. Idempotent: granting twice is a no-op. */
    @Transactional
    public void grantPermission(
            Long roleId,
            Long permissionId) {
        Role role = roles.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบทบาท"));
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสิทธิ์"));

        RolePermissionId id = new RolePermissionId(roleId, permissionId);
        Optional<RolePermission> existing = rolePermissions.findById(id);

        if (existing.isEmpty()) {
            rolePermissions.save(new RolePermission(role, permission));
        }
    }

    @Transactional
    public void revokePermission(
            Long roleId,
            Long permissionId) {
        rolePermissions.deleteById(new RolePermissionId(roleId, permissionId));
    }
}
