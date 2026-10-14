package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Appointment branch scope (R1–R3). Session selection and additional branch grants await Q9. */
@Component
@Transactional(readOnly = true)
public class BranchAccessPolicy {
    private final CurrentActor actor;
    private final AppUserRepository users;
    private final UserRoleRepository roles;
    private final BranchRepository branches;

    public BranchAccessPolicy(CurrentActor actor, AppUserRepository users,
                              UserRoleRepository roles, BranchRepository branches) {
        this.actor = actor;
        this.users = users;
        this.roles = roles;
        this.branches = branches;
    }

    /** Null means all branches, and is returned only for a current database Manager. */
    public Branch scope(String requestedCode) {
        Long userId = actor.requireId();
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String code = requestedCode == null || requestedCode.isBlank() ? null : requestedCode.trim();
        boolean manager = roles.findByUserId(userId).stream()
                .anyMatch(role -> "Manager".equalsIgnoreCase(role.getRole().getRoleName()));
        if (manager) {
            return code == null ? null : branches.findByBranchCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + code));
        }
        Branch home = user.getStaff() == null ? null : user.getStaff().getBranch();
        if (home == null || (code != null && !home.getBranchCode().equals(code))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เข้าถึงนัดหมายของสาขานี้");
        }
        return home;
    }

    public void requireAccess(Branch branch) {
        scope(branch.getBranchCode());
    }
}
