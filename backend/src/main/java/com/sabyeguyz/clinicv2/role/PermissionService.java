package com.sabyeguyz.clinicv2.role;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {
    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Permission> list() {
        return repository.findAll();
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
