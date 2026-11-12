package com.sabyeguyz.clinicv2.auth;

import java.io.Serializable;
import java.util.Objects;

public class UserBranchAccessId implements Serializable {
    private Long user;
    private Long branch;

    public UserBranchAccessId() {}

    public UserBranchAccessId(
            Long user,
            Long branch) {
        this.user = user;
        this.branch = branch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof UserBranchAccessId that)) return false;

        return Objects.equals(user, that.user) && Objects.equals(branch, that.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, branch);
    }
}
