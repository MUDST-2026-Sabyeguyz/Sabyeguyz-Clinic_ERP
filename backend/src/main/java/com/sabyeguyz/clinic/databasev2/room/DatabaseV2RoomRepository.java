package com.sabyeguyz.clinic.databasev2.room;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2RoomRepository {

    public record RoomRecord(
            Long id,
            Long branchId,
            String branchCode,
            String branchName,
            String roomName,
            String roomType,
            boolean active
    ) {}

    private final JdbcTemplate jdbc;

    private static final String SELECT_ROOMS = """
            select r.room_id, r.branch_id, b.branch_code, b.branch_name,
                   r.room_name, r.room_type, r.room_status
            from room r
            join branch b on b.branch_id = r.branch_id
            """;

    private static final RowMapper<RoomRecord> ROW_MAPPER = (rs, rowNum) -> new RoomRecord(
            rs.getLong("room_id"),
            rs.getLong("branch_id"),
            rs.getString("branch_code"),
            rs.getString("branch_name"),
            rs.getString("room_name"),
            rs.getString("room_type"),
            rs.getBoolean("room_status")
    );

    public DatabaseV2RoomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoomRecord> findAll() {
        return jdbc.query(SELECT_ROOMS + " order by r.room_name", ROW_MAPPER);
    }

    public List<RoomRecord> findByBranch(Long branchId) {
        return jdbc.query(SELECT_ROOMS + " where r.branch_id = ? order by r.room_name", ROW_MAPPER, branchId);
    }

    public Optional<RoomRecord> findById(Long id) {
        return jdbc.query(SELECT_ROOMS + " where r.room_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public RoomRecord create(Long branchId, String name, String type, boolean active, Long actorId) {
        Long id = jdbc.queryForObject(
                """
                insert into room (branch_id, room_name, room_type, room_status, room_create_at, room_create_by)
                values (?, ?, ?, ?, ?, ?)
                returning room_id
                """,
                Long.class, branchId, name.trim(), type != null ? type.trim() : "General", active,
                LocalDateTime.now(), actorId
        );
        return findById(id).orElseThrow();
    }

    public RoomRecord update(Long id, String name, String type, boolean active, Long actorId) {
        jdbc.update(
                """
                update room set room_name = ?, room_type = ?, room_status = ?,
                                room_update_at = ?, room_update_by = ?
                where room_id = ?
                """,
                name.trim(), type != null ? type.trim() : "General", active,
                LocalDateTime.now(), actorId, id
        );
        return findById(id).orElseThrow();
    }

    public void setStatus(Long id, boolean active, Long actorId) {
        jdbc.update(
                "update room set room_status = ?, room_update_at = ?, room_update_by = ? where room_id = ?",
                active, LocalDateTime.now(), actorId, id
        );
    }
}
