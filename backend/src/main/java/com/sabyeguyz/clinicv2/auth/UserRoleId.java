package com.sabyeguyz.clinicv2.auth;

import java.io.Serializable;
import java.util.Objects;

public class UserRoleId implements Serializable {
    private Long user;
    private Long role;

    public UserRoleId() {}

    public UserRoleId(Long user, Long role) {
        this.user = user;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRoleId that)) return false;
        return Objects.equals(user, that.user) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() { return Objects.hash(user, role); }
}
