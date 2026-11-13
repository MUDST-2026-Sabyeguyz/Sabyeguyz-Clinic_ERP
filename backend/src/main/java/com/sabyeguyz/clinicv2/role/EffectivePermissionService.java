package com.sabyeguyz.clinicv2.role;

import com.sabyeguyz.clinicv2.auth.AppUser;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class EffectivePermissionService {
    private final AppUserRepository users;
    private final UserPermissionRepository userPermissions;
    private final RolePermissionRepository rolePermissions;

    public EffectivePermissionService(
            AppUserRepository users,
            UserPermissionRepository userPermissions,
            RolePermissionRepository rolePermissions) {
        this.users = users;
        this.userPermissions = userPermissions;
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public boolean isAllowed(Long userId, Long permissionId) {
        if (userId == null || permissionId == null) {
            return false;
        }
        boolean active = users.findById(userId)
                .map(AppUser::isUserStatus)
                .orElse(false);
        if (!active) {
            return false;
        }
        var override = userPermissions
                .findByUserIdAndPermissionId(userId, permissionId);
        if (override.isPresent()) {
            return override.get().isAllowed();
        }
        return rolePermissions.countGrantedToUser(
                userId, permissionId) > 0;
    }

    @Transactional(readOnly = true)
    public void requireAllowed(Long userId, Long permissionId) {
        if (!isAllowed(userId, permissionId)) {
            throw new AccessDeniedException("ไม่มีสิทธิ์ทำรายการนี้");
        }
    }
}