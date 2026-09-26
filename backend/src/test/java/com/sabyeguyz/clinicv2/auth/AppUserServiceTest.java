package com.sabyeguyz.clinicv2.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.role.Role;
import com.sabyeguyz.clinicv2.role.RoleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {
    @Mock AppUserRepository users;
    @Mock RoleRepository roles;
    @Mock UserRoleRepository userRoles;
    @Mock BranchRepository branches;
    @Mock UserBranchAccessRepository userBranchAccess;

    private AppUserService service() {
        return new AppUserService(users, roles, userRoles, branches, userBranchAccess);
    }

    @Test
    void registerHashesThePassword() {
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service().register("alice", "password123", null, 1L);

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPasswordHash()).isNotEqualTo("password123");
        assertThat(user.isUserStatus()).isTrue();
    }

    @Test
    void rejectsShortPassword() {
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().register("alice", "short", null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginRejectsWrongPassword() {
        AppUser existing = new AppUser();
        existing.setUsername("alice");
        existing.setPasswordHash(new BCryptPasswordEncoder().encode("password123"));
        existing.setUserStatus(true);
        when(users.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().login("alice", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginRejectsDeactivatedUser() {
        AppUser existing = new AppUser();
        existing.setUsername("alice");
        existing.setPasswordHash(new BCryptPasswordEncoder().encode("password123"));
        existing.setUserStatus(false);
        when(users.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().login("alice", "password123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantBranchAccessIsIdempotent() {
        AppUser user = new AppUser();
        Branch branch = new Branch();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(userBranchAccess.findById(new UserBranchAccessId(1L, 2L)))
                .thenReturn(Optional.of(new UserBranchAccess(user, branch)));

        service().grantBranchAccess(1L, 2L);

        verify(userBranchAccess, never()).save(any());
    }

    @Test
    void assignRoleSavesWhenNotAlreadyAssigned() {
        AppUser user = new AppUser();
        Role role = new Role();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(roles.findById(3L)).thenReturn(Optional.of(role));
        when(userRoles.findById(new com.sabyeguyz.clinicv2.auth.UserRoleId(1L, 3L))).thenReturn(Optional.empty());

        service().assignRole(1L, 3L);

        verify(userRoles).save(any());
    }
}
