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
        Set<String> canonical = roles.stream().map(DatabaseV2Role::name)
                .map(name -> name.trim().toUpperCase(java.util.Locale.ROOT))
                .filter(name -> "MANAGER".equals(name) || "THERAPIST".equals(name))
                .collect(java.util.stream.Collectors.toSet());
        if (canonical.contains("MANAGER")) return "MANAGER";
        if (canonical.contains("THERAPIST")) return "THERAPIST";
        throw new IllegalArgumentException("บัญชีผู้ใช้ไม่มีบทบาทที่ใช้งานได้");
    }
}
