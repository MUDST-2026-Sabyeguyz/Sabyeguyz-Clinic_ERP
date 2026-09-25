package com.sabyeguyz.clinicv2.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
    @Mock RoleRepository roles;
    @Mock PermissionRepository permissions;
    @Mock RolePermissionRepository rolePermissions;

    @Test
    void createsRole() {
        when(roles.findByRoleName("MANAGER")).thenReturn(Optional.empty());
        when(roles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Role role = new RoleService(roles, permissions, rolePermissions).create("MANAGER", 1L);

        assertThat(role.getRoleName()).isEqualTo("MANAGER");
    }

    @Test
    void rejectsDuplicateRoleName() {
        when(roles.findByRoleName("MANAGER")).thenReturn(Optional.of(new Role()));
        assertThatThrownBy(() -> new RoleService(roles, permissions, rolePermissions).create("MANAGER", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantPermissionIsIdempotent() {
        Role role = new Role();
        Permission permission = new Permission();
        when(roles.findById(1L)).thenReturn(Optional.of(role));
        when(permissions.findById(2L)).thenReturn(Optional.of(permission));
        when(rolePermissions.findById(new RolePermissionId(1L, 2L))).thenReturn(Optional.of(new RolePermission(role, permission)));

        new RoleService(roles, permissions, rolePermissions).grantPermission(1L, 2L);

        verify(rolePermissions, never()).save(any());
    }
}
