package com.sabyeguyz.clinicv2.role;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permission")
@IdClass(RolePermissionId.class)
public class RolePermission {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    public RolePermission() {}

    public RolePermission(
            Role role,
            Permission permission) {
        this.role = role;
        this.permission = permission;
    }

    public Role getRole() { return role; }

    public Permission getPermission() { return permission; }
}
