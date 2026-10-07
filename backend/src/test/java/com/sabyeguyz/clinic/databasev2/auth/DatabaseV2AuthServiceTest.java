package com.sabyeguyz.clinic.databasev2.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DatabaseV2AuthServiceTest {
    private final DatabaseV2AppUserRepository users = mock(DatabaseV2AppUserRepository.class);
    private final DatabaseV2AuthService service = new DatabaseV2AuthService(users);

    @Test
    void loginUsesUserRoleTableModelAndRecordsLoginTime() {
        DatabaseV2AppUser user = new DatabaseV2AppUser(7L, "manager",
                new BCryptPasswordEncoder().encode("password123"), true,
                Set.of(new DatabaseV2Role(1L, "MANAGER")));
        when(users.findByUsername("manager")).thenReturn(Optional.of(user));

        DatabaseV2AppUser result = service.login(" manager ", "password123");

        assertThat(result.primaryRole()).isEqualTo("MANAGER");
        verify(users).recordLogin(eq(7L), any());
    }

    @Test
    void loginRejectsInactiveUsers() {
        DatabaseV2AppUser user = new DatabaseV2AppUser(8L, "inactive",
                new BCryptPasswordEncoder().encode("password123"), false,
                Set.of(new DatabaseV2Role(2L, "THERAPIST")));
        when(users.findByUsername("inactive")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("inactive", "password123"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
    @Test
    void historicalRoleCasingIsCanonicalAndUnsupportedRolesAreNotGranted() {
        var manager = new DatabaseV2AppUser(7L, "manager", "hash", true,
                Set.of(new DatabaseV2Role(1L, "Manager"), new DatabaseV2Role(2L, "Therapist")));
        assertThat(manager.primaryRole()).isEqualTo("MANAGER");
        var therapist = new DatabaseV2AppUser(8L, "therapist", "hash", true,
                Set.of(new DatabaseV2Role(2L, "Therapist")));
        assertThat(therapist.primaryRole()).isEqualTo("THERAPIST");
        var unsupported = new DatabaseV2AppUser(9L, "unsupported", "hash", true,
                Set.of(new DatabaseV2Role(3L, "Receptionist")));
        assertThatThrownBy(unsupported::primaryRole).isInstanceOf(IllegalArgumentException.class);
    }

}
