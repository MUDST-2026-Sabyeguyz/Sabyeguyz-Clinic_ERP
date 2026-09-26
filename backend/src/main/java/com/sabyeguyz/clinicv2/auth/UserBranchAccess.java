package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.branch.Branch;
import jakarta.persistence.*;

@Entity
@Table(name = "user_branch_access")
@IdClass(UserBranchAccessId.class)
public class UserBranchAccess {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    public UserBranchAccess() {}

    public UserBranchAccess(AppUser user, Branch branch) {
        this.user = user;
        this.branch = branch;
    }

    public AppUser getUser() { return user; }
    public Branch getBranch() { return branch; }
}
