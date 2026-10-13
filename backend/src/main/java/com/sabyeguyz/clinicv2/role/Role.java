package com.sabyeguyz.clinicv2.role;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "role")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;

    @org.hibernate.annotations.ColumnTransformer(read = "cast(role_name as text)", write = "cast(? as role_name_enum)")
    @Column(name = "role_name", length = 50, nullable = false, unique = true)
    private String roleName;

    @Column(name = "role_create_at", nullable = false, updatable = false)
    private Instant roleCreateAt;

    @Column(name = "role_create_by", nullable = false, updatable = false)
    private Long roleCreateBy;

    @Column(name = "role_update_at")
    private Instant roleUpdateAt;

    @Column(name = "role_update_by")
    private Long roleUpdateBy;

    public Role() {}

    public Long getId() { return id; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Instant getRoleCreateAt() { return roleCreateAt; }
    public void setRoleCreateAt(Instant roleCreateAt) { this.roleCreateAt = roleCreateAt; }
    public Long getRoleCreateBy() { return roleCreateBy; }
    public void setRoleCreateBy(Long roleCreateBy) { this.roleCreateBy = roleCreateBy; }
    public Instant getRoleUpdateAt() { return roleUpdateAt; }
    public void setRoleUpdateAt(Instant roleUpdateAt) { this.roleUpdateAt = roleUpdateAt; }
    public Long getRoleUpdateBy() { return roleUpdateBy; }
    public void setRoleUpdateBy(Long roleUpdateBy) { this.roleUpdateBy = roleUpdateBy; }
}
