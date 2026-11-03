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
            if (code == null) return null;
            return requireOpen(branches.findByBranchCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + code)));
        }
        Branch home = user.getStaff() == null ? null : user.getStaff().getBranch();
        if (home == null || (code != null && !home.getBranchCode().equals(code))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เข้าถึงนัดหมายของสาขานี้");
        }
        return requireOpen(home);
    }

    /** A branch that is closed (ปิดใช้งาน) allows nothing at all: nobody can work in it until it is opened again. */
    private static Branch requireOpen(Branch branch) {
        if (!branch.isBranchStatus()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "สาขา " + branch.getBranchName() + " ปิดใช้งาน ไม่สามารถทำรายการที่สาขานี้ได้");
        }
        return branch;
    }

    /** True for a current database Manager. */
    public boolean isManager() {
        return roles.findByUserId(actor.requireId()).stream()
                .anyMatch(role -> "Manager".equalsIgnoreCase(role.getRole().getRoleName()));
    }

    /** Only the Manager changes an appointment (status, reschedule, cancel); anyone else only views and books their own time. */
    public void requireManager() {
        if (!isManager()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "เฉพาะผู้จัดการเท่านั้นที่เปลี่ยนสถานะหรือแก้ไขนัดหมายได้");
        }
    }

    /** The staff id of the signed-in user, or null when the account has no staff record. */
    public Long currentStaffId() {
        AppUser user = users.findById(actor.requireId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return user.getStaff() == null ? null : user.getStaff().getId();
    }

    public void requireAccess(Branch branch) {
        scope(branch.getBranchCode());
    }

    /** Same check, with the text shown when the branch is not the user's own. */
    public void requireAccess(Branch branch, String forbiddenMessage) {
        try {
            scope(branch.getBranchCode());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
            }
            throw exception;
        }
    }
}
