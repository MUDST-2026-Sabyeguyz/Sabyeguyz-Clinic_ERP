package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.role.Role;
import jakarta.persistence.*;

@Entity
@Table(name = "user_role")
@IdClass(UserRoleId.class)
public class UserRole {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    protected UserRole() {}

    public UserRole(AppUser user, Role role) {
        this.user = user;
        this.role = role;
    }

    public AppUser getUser() { return user; }
    public Role getRole() { return role; }
}
