package com.sabyeguyz.clinic.databasev2.auth;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2AppUserRepository {
    private final JdbcTemplate jdbc;

    public DatabaseV2AppUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DatabaseV2AppUser> findByUsername(String username) {
        List<UserRow> users = jdbc.query(
                "select user_id, username, password_hash, user_status from app_user where username = ?",
                (rs, rowNum) -> new UserRow(rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getBoolean("user_status")),
                username);
        if (users.isEmpty()) return Optional.empty();

        UserRow user = users.get(0);
        Set<DatabaseV2Role> roles = new LinkedHashSet<>(jdbc.query(
                "select r.role_id, r.role_name from role r "
                        + "join user_role ur on ur.role_id = r.role_id where ur.user_id = ? order by r.role_name",
                (rs, rowNum) -> new DatabaseV2Role(rs.getLong("role_id"), rs.getString("role_name")),
                user.id()));
        return Optional.of(new DatabaseV2AppUser(
                user.id(), user.username(), user.passwordHash(), user.active(), roles));
    }

    public void recordLogin(Long userId, LocalDateTime time) {
        jdbc.update("update app_user set last_login_at = ? where user_id = ?", time, userId);
    }

    private record UserRow(Long id, String username, String passwordHash, boolean active) {}
}
