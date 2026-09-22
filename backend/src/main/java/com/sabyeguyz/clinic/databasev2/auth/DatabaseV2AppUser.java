package com.sabyeguyz.clinic.databasev2.auth;

import java.util.Set;

public record DatabaseV2AppUser(
        Long id,
        String username,
        String passwordHash,
        boolean active,
        Set<DatabaseV2Role> roles) {

    public DatabaseV2AppUser {
        roles = Set.copyOf(roles);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Set<DatabaseV2Role> getRoles() { return roles; }

    public String primaryRole() {
        return roles.stream().map(DatabaseV2Role::name)
                .filter("MANAGER"::equals)
                .findFirst()
                .orElseGet(() -> roles.stream().map(DatabaseV2Role::name).sorted().findFirst()
                        .orElseThrow(() -> new IllegalStateException("ผู้ใช้ยังไม่มี role")));
    }
}
