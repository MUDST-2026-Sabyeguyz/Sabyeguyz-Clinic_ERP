package com.sabyeguyz.clinicv2.auth;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.role.Role;
import com.sabyeguyz.clinicv2.role.RoleRepository;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class AppUserServiceScenarios {
    V2RepositoryStub<AppUserRepository> usersStub = new V2RepositoryStub<>(AppUserRepository.class);
    AppUserRepository users = usersStub.proxy();
    V2RepositoryStub<RoleRepository> rolesStub = new V2RepositoryStub<>(RoleRepository.class);
    RoleRepository roles = rolesStub.proxy();
    V2RepositoryStub<UserRoleRepository> userRolesStub = new V2RepositoryStub<>(UserRoleRepository.class);
    UserRoleRepository userRoles = userRolesStub.proxy();
    V2RepositoryStub<BranchRepository> branchesStub = new V2RepositoryStub<>(BranchRepository.class);
    BranchRepository branches = branchesStub.proxy();
    V2RepositoryStub<UserBranchAccessRepository> userBranchAccessStub = new V2RepositoryStub<>(UserBranchAccessRepository.class);
    UserBranchAccessRepository userBranchAccess = userBranchAccessStub.proxy();

    private AppUserService service() {
        return new AppUserService(users, roles, userRoles, branches, userBranchAccess);
    }

    public Map<String, Object> registerHashesThePassword() {
        Map<String, Object> out = new LinkedHashMap<>();
        usersStub.on("findByUsername", args -> Optional.empty());

        AppUser user = service().register("alice", "alice@example.com", "password123", null, 1L);

        out.put("username", user.getUsername());
        out.put("passwordHash", user.getPasswordHash());
        out.put("userStatus", user.isUserStatus());
        return out;
    }

    public Map<String, Object> rejectsShortPassword() {
        Map<String, Object> out = new LinkedHashMap<>();
        usersStub.on("findByUsername", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> {
            service().register(
                    "alice", "alice@example.com", "short", null, 1L);
            return null;
        }));
        out.put("usersSaveCalls", usersStub.calls("save"));
        return out;
    }

    public Map<String, Object> loginRejectsWrongPassword() {
        Map<String, Object> out = new LinkedHashMap<>();
        AppUser existing = new AppUser();
        existing.setUsername("alice");
        existing.setPasswordHash(new BCryptPasswordEncoder().encode("password123"));
        existing.setUserStatus(true);
        usersStub.on("findByUsername", args -> Optional.of(existing));

        out.putAll(V2RepositoryStub.outcome(() -> { service().login("alice", "wrong"); return null; }));
        return out;
    }

    public Map<String, Object> loginRejectsDeactivatedUser() {
        Map<String, Object> out = new LinkedHashMap<>();
        AppUser existing = new AppUser();
        existing.setUsername("alice");
        existing.setPasswordHash(new BCryptPasswordEncoder().encode("password123"));
        existing.setUserStatus(false);
        usersStub.on("findByUsername", args -> Optional.of(existing));

        out.putAll(V2RepositoryStub.outcome(() -> { service().login("alice", "password123"); return null; }));
        return out;
    }

    public Map<String, Object> grantBranchAccessIsIdempotent() {
        Map<String, Object> out = new LinkedHashMap<>();
        AppUser user = new AppUser();
        Branch branch = new Branch();
        usersStub.on("findById", args -> Optional.of(user));
        branchesStub.on("findById", args -> Optional.of(branch));
        userBranchAccessStub.on("findById", args -> Optional.of(new UserBranchAccess(user, branch)));

        service().grantBranchAccess(1L, 2L);

        out.put("userBranchAccessSaveCalls", userBranchAccessStub.calls("save"));
        return out;
    }

    public Map<String, Object> assignRoleSavesWhenNotAlreadyAssigned() {
        Map<String, Object> out = new LinkedHashMap<>();
        AppUser user = new AppUser();
        Role role = new Role();
        usersStub.on("findById", args -> Optional.of(user));
        rolesStub.on("findById", args -> Optional.of(role));
        userRolesStub.on("findById", args -> Optional.empty());

        service().assignRole(1L, 3L);

        out.put("userRolesSaveCalls", userRolesStub.calls("save"));
        return out;
    }
}
