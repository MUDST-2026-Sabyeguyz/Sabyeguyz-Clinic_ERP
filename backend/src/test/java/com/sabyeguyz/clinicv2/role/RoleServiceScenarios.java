package com.sabyeguyz.clinicv2.role;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import java.util.Optional;

public class RoleServiceScenarios {
    V2RepositoryStub<RoleRepository> rolesStub = new V2RepositoryStub<>(RoleRepository.class);
    RoleRepository roles = rolesStub.proxy();
    V2RepositoryStub<PermissionRepository> permissionsStub = new V2RepositoryStub<>(PermissionRepository.class);
    PermissionRepository permissions = permissionsStub.proxy();
    V2RepositoryStub<RolePermissionRepository> rolePermissionsStub = new V2RepositoryStub<>(RolePermissionRepository.class);
    RolePermissionRepository rolePermissions = rolePermissionsStub.proxy();

    public Map<String, Object> createsRole() {
        Map<String, Object> out = new LinkedHashMap<>();
        rolesStub.on("findByRoleName", args -> Optional.empty());

        Role role = new RoleService(roles, permissions, rolePermissions).create("MANAGER", 1L);

        out.put("roleName", role.getRoleName());
        return out;
    }

    public Map<String, Object> rejectsDuplicateRoleName() {
        Map<String, Object> out = new LinkedHashMap<>();
        rolesStub.on("findByRoleName", args -> Optional.of(new Role()));
        out.putAll(V2RepositoryStub.outcome(() -> { new RoleService(roles, permissions, rolePermissions).create("MANAGER", 1L); return null; }));
        return out;
    }

    public Map<String, Object> grantPermissionIsIdempotent() {
        Map<String, Object> out = new LinkedHashMap<>();
        Role role = new Role();
        Permission permission = new Permission();
        rolesStub.on("findById", args -> Optional.of(role));
        permissionsStub.on("findById", args -> Optional.of(permission));
        rolePermissionsStub.on("findById", args -> Optional.of(new RolePermission(role, permission)));

        new RoleService(roles, permissions, rolePermissions).grantPermission(1L, 2L);

        out.put("rolePermissionsSaveCalls", rolePermissionsStub.calls("save"));
        return out;
    }
}
