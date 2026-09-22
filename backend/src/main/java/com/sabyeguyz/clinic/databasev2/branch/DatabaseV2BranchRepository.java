package com.sabyeguyz.clinic.databasev2.branch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2BranchRepository {
    private static final String COLUMNS =
            "branch_id, branch_code, branch_name, address, phone, branch_status";
    private static final RowMapper<DatabaseV2Branch> ROW_MAPPER = (rs, rowNum) ->
            new DatabaseV2Branch(rs.getLong("branch_id"), rs.getString("branch_code").trim(),
                    rs.getString("branch_name"), rs.getString("address"), rs.getString("phone"),
                    rs.getBoolean("branch_status"));

    private final JdbcTemplate jdbc;

    public DatabaseV2BranchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DatabaseV2Branch> findAllByOrderByName() {
        return jdbc.query("select " + COLUMNS + " from branch order by branch_name", ROW_MAPPER);
    }

    public Optional<DatabaseV2Branch> findById(Long id) {
        return jdbc.query("select " + COLUMNS + " from branch where branch_id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Optional<DatabaseV2Branch> findByCode(String code) {
        return jdbc.query("select " + COLUMNS + " from branch where branch_code = ?", ROW_MAPPER, code)
                .stream().findFirst();
    }

    public DatabaseV2Branch create(String code, String name, String address, String phone, Long actorId) {
        return jdbc.queryForObject(
                "insert into branch (branch_code, branch_name, address, phone, branch_create_by) "
                        + "values (?, ?, ?, ?, ?) returning " + COLUMNS,
                ROW_MAPPER, code, name, address, phone, actorId);
    }

    public DatabaseV2Branch update(Long id, String name, String address, String phone, Long actorId) {
        return jdbc.queryForObject(
                "update branch set branch_name = ?, address = ?, phone = ?, branch_update_at = ?, "
                        + "branch_update_by = ? where branch_id = ? returning " + COLUMNS,
                ROW_MAPPER, name, address, phone, LocalDateTime.now(), actorId, id);
    }

    public void deactivate(Long id, Long actorId) {
        jdbc.update("update branch set branch_status = false, branch_update_at = ?, branch_update_by = ? "
                + "where branch_id = ?", LocalDateTime.now(), actorId, id);
    }
}
