package com.sabyeguyz.clinicv2.role;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "permission")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long id;

    @Column(name = "permission_name", length = 100, nullable = false, unique = true)
    private String permissionName;

    @Column(name = "module_name", length = 50)
    private String moduleName;

    @Column(name = "permission_create_at", nullable = false, updatable = false)
    private Instant permissionCreateAt;

    @Column(name = "permission_create_by", nullable = false, updatable = false)
    private Long permissionCreateBy;

    @Column(name = "permission_update_at")
    private Instant permissionUpdateAt;

    @Column(name = "permission_update_by")
    private Long permissionUpdateBy;

    public Permission() {}

    public Long getId() { return id; }

    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public Instant getPermissionCreateAt() { return permissionCreateAt; }
    public void setPermissionCreateAt(Instant permissionCreateAt) { this.permissionCreateAt = permissionCreateAt; }

    public Long getPermissionCreateBy() { return permissionCreateBy; }
    public void setPermissionCreateBy(Long permissionCreateBy) { this.permissionCreateBy = permissionCreateBy; }

    public Instant getPermissionUpdateAt() { return permissionUpdateAt; }
    public void setPermissionUpdateAt(Instant permissionUpdateAt) { this.permissionUpdateAt = permissionUpdateAt; }

    public Long getPermissionUpdateBy() { return permissionUpdateBy; }
    public void setPermissionUpdateBy(Long permissionUpdateBy) { this.permissionUpdateBy = permissionUpdateBy; }
}
