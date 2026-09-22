package com.sabyeguyz.clinic.databasev2.audit;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2AuditRepository {

    private final JdbcTemplate jdbc;

    public DatabaseV2AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(String tableName, String recordId, String action, String oldValue, String newValue, Long changedBy) {
        jdbc.update(
                """
                insert into audit_log (table_name, record_id, action, old_value, new_value, changed_by, changed_at)
                values (?, ?, cast(? as audit_action_enum), ?, ?, ?, current_timestamp)
                """,
                tableName, recordId, action, oldValue, newValue, changedBy
        );
    }

    public Page<DatabaseV2AuditLogDto> list(int page, int size) {
        Long total = jdbc.queryForObject("select count(*) from audit_log", Long.class);
        if (total == null) total = 0L;

        int offset = page * size;
        List<DatabaseV2AuditLogDto> content = jdbc.query(
                """
                select audit_id, table_name, record_id, cast(action as varchar) as action_str,
                       old_value, new_value, changed_by, changed_at
                from audit_log
                order by audit_id desc
                limit ? offset ?
                """,
                (rs, rowNum) -> DatabaseV2AuditLogDto.of(
                        rs.getLong("audit_id"),
                        rs.getString("table_name"),
                        rs.getString("record_id"),
                        rs.getString("action_str"),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getLong("changed_by"),
                        rs.getTimestamp("changed_at").toInstant()
                ),
                size, offset
        );

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }
}
