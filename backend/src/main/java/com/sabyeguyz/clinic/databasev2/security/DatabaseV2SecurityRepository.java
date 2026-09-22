package com.sabyeguyz.clinic.databasev2.security;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2SecurityRepository {
    private final JdbcTemplate jdbc;

    public DatabaseV2SecurityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RoleRecord(Long id, String name) {}
    public record PermissionRecord(Long id, String name, String module) {}

    public List<RoleRecord> findAllRoles() {
        return jdbc.query("select role_id, role_name from role order by role_name",
                (rs, rowNum) -> new RoleRecord(rs.getLong("role_id"), rs.getString("role_name")));
    }

    public Optional<RoleRecord> findRoleByName(String name) {
        return jdbc.query("select role_id, role_name from role where role_name = ?",
                (rs, rowNum) -> new RoleRecord(rs.getLong("role_id"), rs.getString("role_name")),
                name).stream().findFirst();
    }

    public RoleRecord getOrCreateRole(String name, Long actorId) {
        return findRoleByName(name).orElseGet(() -> {
            Long id = jdbc.queryForObject(
                    "insert into role (role_name, role_create_by) values (?, ?) returning role_id",
                    Long.class, name, actorId);
            return new RoleRecord(id, name);
        });
    }

    public void assignRoleToUser(Long userId, Long roleId) {
        jdbc.update("insert into user_role (user_id, role_id) values (?, ?) on conflict do nothing",
                userId, roleId);
    }

    public void removeRolesFromUser(Long userId) {
        jdbc.update("delete from user_role where user_id = ?", userId);
    }

    public List<PermissionRecord> findAllPermissions() {
        return jdbc.query("select permission_id, permission_name, module_name from permission order by permission_name",
                (rs, rowNum) -> new PermissionRecord(rs.getLong("permission_id"),
                        rs.getString("permission_name"), rs.getString("module_name")));
    }

    public Optional<PermissionRecord> findPermissionByName(String name) {
        return jdbc.query("select permission_id, permission_name, module_name from permission where permission_name = ?",
                (rs, rowNum) -> new PermissionRecord(rs.getLong("permission_id"),
                        rs.getString("permission_name"), rs.getString("module_name")),
                name).stream().findFirst();
    }

    public PermissionRecord getOrCreatePermission(String name, String module, Long actorId) {
        return findPermissionByName(name).orElseGet(() -> {
            Long id = jdbc.queryForObject(
                    "insert into permission (permission_name, module_name, permission_create_by) values (?, ?, ?) returning permission_id",
                    Long.class, name, module, actorId);
            return new PermissionRecord(id, name, module);
        });
    }

    public void assignBranchAccess(Long userId, Long branchId) {
        jdbc.update("insert into user_branch_access (user_id, branch_id) values (?, ?) on conflict do nothing",
                userId, branchId);
    }

    public void removeBranchAccess(Long userId, Long branchId) {
        jdbc.update("delete from user_branch_access where user_id = ? and branch_id = ?", userId, branchId);
    }

    public List<Long> findBranchAccessByUserId(Long userId) {
        return jdbc.queryForList("select branch_id from user_branch_access where user_id = ?", Long.class, userId);
    }
}
