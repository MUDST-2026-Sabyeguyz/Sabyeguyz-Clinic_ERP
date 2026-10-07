package com.sabyeguyz.clinic.databasev2.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2ServiceRepository {

    public record ServiceModel(
            Long id,
            String code,
            String name,
            String type,
            BigDecimal price,
            boolean active,
            Integer totalVisits,
            Integer bonusVisits,
            Integer validityDays,
            int durationMinutes
    ) {
        public ServiceModel(Long id, String code, String name, String type, BigDecimal price, boolean active,
                            Integer totalVisits, Integer bonusVisits, Integer validityDays) {
            this(id,code,name,type,price,active,totalVisits,bonusVisits,validityDays,60);
        }
    }

    private final JdbcTemplate jdbc;

    private static final String SELECT_SERVICE = """
            select service_id, service_code, service_name, cast(service_type as varchar) as service_type,
                   service_price, service_status, default_total_visits, default_bonus_visits, default_validity_days, default_duration_minutes
            from service
            """;

    private static final RowMapper<ServiceModel> ROW_MAPPER = (rs, rowNum) -> new ServiceModel(
            rs.getLong("service_id"),
            rs.getString("service_code"),
            rs.getString("service_name"),
            rs.getString("service_type"),
            rs.getBigDecimal("service_price"),
            rs.getBoolean("service_status"),
            (Integer) rs.getObject("default_total_visits"),
            (Integer) rs.getObject("default_bonus_visits"),
            (Integer) rs.getObject("default_validity_days"),
            rs.getInt("default_duration_minutes")
    );

    public DatabaseV2ServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceModel> findAll() {
        return jdbc.query(SELECT_SERVICE + " order by service_name", ROW_MAPPER);
    }

    public List<ServiceModel> findByType(String type) {
        return jdbc.query(SELECT_SERVICE + " where service_type = cast(? as service_type_enum) order by service_name",
                ROW_MAPPER, type);
    }

    public List<ServiceModel> findActive() {
        return jdbc.query(SELECT_SERVICE + " where service_status = true order by service_name", ROW_MAPPER);
    }

    public List<ServiceModel> findActiveByType(String type) {
        return jdbc.query(SELECT_SERVICE + " where service_status = true and service_type = cast(? as service_type_enum) order by service_name",
                ROW_MAPPER, type);
    }

    public Optional<ServiceModel> findById(Long id) {
        return jdbc.query(SELECT_SERVICE + " where service_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<ServiceModel> findForBooking(Long id) {
        return jdbc.query(SELECT_SERVICE + " where service_id = ? for share", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<ServiceModel> findByCode(String code) {
        return jdbc.query(SELECT_SERVICE + " where service_code = ?", ROW_MAPPER, code.trim().toUpperCase()).stream().findFirst();
    }

    public ServiceModel create(String code, String name, String type, BigDecimal price,
                               Integer totalVisits, Integer bonusVisits, Integer validityDays,
                               boolean active, Long actorId) {
        Long id = jdbc.queryForObject(
                """
                insert into service (
                    service_code, service_name, service_type, service_price,
                    default_total_visits, default_bonus_visits, default_validity_days,
                    service_status, service_create_at, service_create_by
                ) values (
                    ?, ?, cast(? as service_type_enum), ?,
                    ?, ?, ?,
                    ?, ?, ?
                ) returning service_id
                """,
                Long.class,
                code.trim().toUpperCase(), name.trim(), type, price,
                totalVisits != null && totalVisits >= 1 ? totalVisits : (type.equals("Course") ? 10 : 1),
                bonusVisits != null && bonusVisits >= 0 ? bonusVisits : 0,
                validityDays != null && validityDays >= 0 ? validityDays : 365,
                active, LocalDateTime.now(), actorId
        );
        return findById(id).orElseThrow();
    }

    public ServiceModel update(Long id, String code, String name, String type, BigDecimal price,
                               Integer totalVisits, Integer bonusVisits, Integer validityDays,
                               boolean active, Long actorId) {
        jdbc.update(
                """
                update service set
                    service_code = ?, service_name = ?, service_type = cast(? as service_type_enum),
                    service_price = ?, default_total_visits = ?, default_bonus_visits = ?,
                    default_validity_days = ?, service_status = ?, service_update_at = ?, service_update_by = ?
                where service_id = ?
                """,
                code.trim().toUpperCase(), name.trim(), type, price,
                totalVisits != null && totalVisits >= 1 ? totalVisits : (type.equals("Course") ? 10 : 1),
                bonusVisits != null && bonusVisits >= 0 ? bonusVisits : 0,
                validityDays != null && validityDays >= 0 ? validityDays : 365,
                active, LocalDateTime.now(), actorId, id
        );
        return findById(id).orElseThrow();
    }

    public void setStatus(Long id, boolean active, Long actorId) {
        jdbc.update(
                "update service set service_status = ?, service_update_at = ?, service_update_by = ? where service_id = ?",
                active, LocalDateTime.now(), actorId, id
        );
    }

    public void setDuration(Long id, int minutes) {
        if (minutes < 1) throw new IllegalArgumentException("ระยะเวลาบริการต้องมากกว่า 0");
        jdbc.update("update service set default_duration_minutes=? where service_id=?",minutes,id);
    }
}
