package com.sabyeguyz.clinic.databasev2.masterdata;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2MasterDataRepository {

    public record MasterDataRecord(
            Long id,
            Long typeId,
            String category,
            String code,
            String name,
            boolean active,
            Integer sortOrder
    ) {}

    private final JdbcTemplate jdbc;

    private static final String SELECT_VALUES = """
            select v.value_id, v.type_id, t.type_name, v.value_name, v.value_status, v.sort_order
            from master_data_value v
            join master_data_type t on t.type_id = v.type_id
            """;

    private static final RowMapper<MasterDataRecord> ROW_MAPPER = (rs, rowNum) -> new MasterDataRecord(
            rs.getLong("value_id"),
            rs.getLong("type_id"),
            rs.getString("type_name"),
            rs.getString("value_name"),
            rs.getString("value_name"),
            rs.getBoolean("value_status"),
            (Integer) rs.getObject("sort_order")
    );

    public DatabaseV2MasterDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MasterDataRecord> findAll() {
        return jdbc.query(SELECT_VALUES + " order by t.type_name, v.sort_order nulls last, v.value_name", ROW_MAPPER);
    }

    public List<MasterDataRecord> findByCategory(String category) {
        return jdbc.query(SELECT_VALUES + " where upper(t.type_name) = upper(?) order by v.sort_order nulls last, v.value_name",
                ROW_MAPPER, category.trim());
    }

    public Optional<MasterDataRecord> findById(Long id) {
        return jdbc.query(SELECT_VALUES + " where v.value_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Long getOrCreateTypeId(String category, Long actorId) {
        String cleanCategory = category.trim().toUpperCase();
        List<Long> ids = jdbc.query(
                "select type_id from master_data_type where upper(type_name) = ?",
                (rs, rowNum) -> rs.getLong("type_id"),
                cleanCategory
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject(
                "insert into master_data_type (type_name, type_create_at, type_create_by) values (?, ?, ?) returning type_id",
                Long.class, cleanCategory, LocalDateTime.now(), actorId
        );
    }

    public MasterDataRecord create(String category, String name, Integer sortOrder, boolean active, Long actorId) {
        Long typeId = getOrCreateTypeId(category, actorId);
        Long id = jdbc.queryForObject(
                """
                insert into master_data_value (
                    type_id, value_name, value_status, sort_order,
                    value_create_at, value_create_by
                ) values (?, ?, ?, ?, ?, ?)
                returning value_id
                """,
                Long.class,
                typeId, name.trim(), active, sortOrder != null && sortOrder >= 0 ? sortOrder : 0,
                LocalDateTime.now(), actorId
        );
        return findById(id).orElseThrow();
    }

    public MasterDataRecord update(Long id, String name, Integer sortOrder, boolean active, Long actorId) {
        jdbc.update(
                """
                update master_data_value set
                    value_name = ?, value_status = ?, sort_order = ?,
                    value_update_at = ?, value_update_by = ?
                where value_id = ?
                """,
                name.trim(), active, sortOrder != null && sortOrder >= 0 ? sortOrder : 0,
                LocalDateTime.now(), actorId, id
        );
        return findById(id).orElseThrow();
    }

    public void setStatus(Long id, boolean active, Long actorId) {
        jdbc.update(
                "update master_data_value set value_status = ?, value_update_at = ?, value_update_by = ? where value_id = ?",
                active, LocalDateTime.now(), actorId, id
        );
    }
}
