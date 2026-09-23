package com.sabyeguyz.clinic.databasev2.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class DatabaseV2BootstrapRunnerTest {

    private JdbcTemplate jdbc;
    private DatabaseV2BootstrapRunner runner;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        runner = new DatabaseV2BootstrapRunner(jdbc, "admin", "test-only-password");
    }

    @Test
    void bootstrapRejectsMissingPasswordBeforeDatabaseWrites() {
        DatabaseV2BootstrapRunner missingPassword = new DatabaseV2BootstrapRunner(jdbc, "admin", " ");
        assertThatThrownBy(missingPassword::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");
        verifyNoInteractions(jdbc);
    }

    @Test
    void bootstrapRotatesAnExistingAdminPassword() {
        when(jdbc.queryForList(eq("select user_id from app_user where username = ?"), eq(Long.class), eq("admin")))
                .thenReturn(List.of(1L));
        when(jdbc.queryForList(eq("select password_hash from app_user where user_id = ?"), eq(String.class), eq(1L)))
                .thenReturn(List.of(new BCryptPasswordEncoder().encode("old-password")));

        ReflectionTestUtils.invokeMethod(runner, "getOrCreateAdminUser", "admin", "test-only-password");

        verify(jdbc).update(eq("update app_user set password_hash = ? where user_id = ?"),
                argThat(hash -> hash instanceof String value
                        && new BCryptPasswordEncoder().matches("test-only-password", value)), eq(1L));
    }

    @Test
    void runExecutesBootstrapIdempotently() {
        // Mock app_user query returning existing admin
        when(jdbc.queryForList(eq("select user_id from app_user where username = ?"), eq(Long.class), eq("admin")))
                .thenReturn(List.of(1L));
        when(jdbc.queryForList(eq("select password_hash from app_user where user_id = ?"), eq(String.class), eq(1L)))
                .thenReturn(List.of(new BCryptPasswordEncoder().encode("test-only-password")));

        // Mock branch query returning existing 01
        when(jdbc.queryForList(eq("select branch_id from branch where branch_code = ?"), eq(Long.class), eq("01")))
                .thenReturn(List.of(1L));

        // Mock role query
        when(jdbc.queryForList(eq("select role_id from role where role_name = ?"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));

        // Mock staff query
        when(jdbc.queryForList(eq("select staff_id from staff where staff_code = ?"), eq(Long.class), eq("ST-MGR-01")))
                .thenReturn(List.of(1L));

        // Mock payment method
        when(jdbc.queryForList(eq("select payment_method_id from payment_method where payment_method_name = ?"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));

        // Mock room
        when(jdbc.queryForList(eq("select room_id from room where branch_id = ? and room_name = ?"), eq(Long.class), eq(1L), anyString()))
                .thenReturn(List.of(1L));

        // Mock master data type
        when(jdbc.queryForList(eq("select type_id from master_data_type where type_name = ?"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));

        // Mock master data value
        when(jdbc.queryForList(eq("select value_id from master_data_value where type_id = ? and value_name = ?"), eq(Long.class), eq(1L), anyString()))
                .thenReturn(List.of(1L));

        // Mock service
        when(jdbc.queryForList(eq("select service_id from service where service_code = ?"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));

        // Mock permission
        when(jdbc.queryForList(eq("select permission_id from permission where permission_name = ?"), eq(Long.class), anyString()))
                .thenReturn(List.of(1L));

        // Mock commission scheme
        when(jdbc.queryForList(eq("select commission_scheme_id from commission_scheme where scheme_name = ? and scheme_version = ?"), eq(Long.class), anyString(), eq(1)))
                .thenReturn(List.of(1L));

        runner.run(new DefaultApplicationArguments());

        // Verify update was called to link admin to staff
        verify(jdbc).update(eq("update app_user set staff_id = ? where user_id = ? and staff_id is null"), eq(1L), eq(1L));
    }
}
