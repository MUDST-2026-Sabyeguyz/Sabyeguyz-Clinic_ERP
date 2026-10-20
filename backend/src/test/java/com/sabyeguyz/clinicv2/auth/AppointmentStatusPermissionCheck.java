package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.role.Role;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.util.List;
import java.util.Optional;
import org.springframework.web.server.ResponseStatusException;

/** Standalone regression check; runs without a database or additional test dependencies. */
public class AppointmentStatusPermissionCheck {
    public static void main(String[] args) throws Exception {
        var users = new V2RepositoryStub<>(AppUserRepository.class);
        var roles = new V2RepositoryStub<>(UserRoleRepository.class);
        CurrentActor actor = new CurrentActor(users.proxy()) {
            @Override public Long requireId() { return 1L; }
        };
        Branch branch = new Branch();
        branch.setBranchCode("02");
        branch.setBranchStatus(true);
        Staff staff = new Staff();
        staff.setBranch(branch);
        AppUser user = new AppUser();
        user.setStaff(staff);
        users.on("findById", values -> Optional.of(user));
        var policy = new BranchAccessPolicy(actor, users.proxy(), roles.proxy(), null);
        var permission = BranchAccessPolicy.class.getMethod("requireAppointmentStatusEditor");
        for (String name : List.of("Manager", "Therapist")) {
            Role role = new Role();
            role.setRoleName(name);
            roles.on("findByUserId", values -> List.of(new UserRole(user, role)));
            permission.invoke(policy);
        }
        // A therapist cannot use the broader status permission across branches.
        try { policy.scope("01"); throw new AssertionError("Cross-branch access allowed"); }
        catch (ResponseStatusException expected) {
            if (expected.getStatusCode().value() != 403) throw expected;
        }
        if (policy.scope("02") != branch) throw new AssertionError("Home branch denied");
        roles.on("findByUserId", values -> List.of());
        try { permission.invoke(policy); throw new AssertionError("Unknown role allowed"); }
        catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof ResponseStatusException denied)
                    || denied.getStatusCode().value() != 403) throw expected;
        }
        System.out.println("PASS: Manager/Therapist status permission, branch scope, unknown-role denial");
    }
}
