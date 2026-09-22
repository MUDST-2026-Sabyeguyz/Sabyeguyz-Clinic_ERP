package com.sabyeguyz.clinic.databasev2.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2PaymentMethodRepository {

    public record PaymentMethodRecord(
            Long id,
            String code,
            String name,
            boolean active
    ) {}

    private final JdbcTemplate jdbc;

    private static final String SELECT_METHODS = """
            select payment_method_id, payment_method_name, payment_method_status
            from payment_method
            """;

    private static final RowMapper<PaymentMethodRecord> ROW_MAPPER = (rs, rowNum) -> new PaymentMethodRecord(
            rs.getLong("payment_method_id"),
            rs.getString("payment_method_name"),
            rs.getString("payment_method_name"),
            rs.getBoolean("payment_method_status")
    );

    public DatabaseV2PaymentMethodRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PaymentMethodRecord> findAll() {
        return jdbc.query(SELECT_METHODS + " order by payment_method_name", ROW_MAPPER);
    }

    public List<PaymentMethodRecord> findActive() {
        return jdbc.query(SELECT_METHODS + " where payment_method_status = true order by payment_method_name", ROW_MAPPER);
    }

    public Optional<PaymentMethodRecord> findById(Long id) {
        return jdbc.query(SELECT_METHODS + " where payment_method_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<PaymentMethodRecord> findByName(String name) {
        return jdbc.query(SELECT_METHODS + " where lower(payment_method_name) = lower(?)", ROW_MAPPER, name.trim()).stream().findFirst();
    }

    public PaymentMethodRecord create(String name, boolean active, Long actorId) {
        Long id = jdbc.queryForObject(
                """
                insert into payment_method (payment_method_name, payment_method_status, payment_method_create_at, payment_method_create_by)
                values (?, ?, ?, ?)
                returning payment_method_id
                """,
                Long.class, name.trim(), active, LocalDateTime.now(), actorId
        );
        return findById(id).orElseThrow();
    }

    public PaymentMethodRecord update(Long id, String name, boolean active, Long actorId) {
        jdbc.update(
                """
                update payment_method set payment_method_name = ?, payment_method_status = ?,
                                          payment_method_update_at = ?, payment_method_update_by = ?
                where payment_method_id = ?
                """,
                name.trim(), active, LocalDateTime.now(), actorId, id
        );
        return findById(id).orElseThrow();
    }

    public void setStatus(Long id, boolean active, Long actorId) {
        jdbc.update(
                "update payment_method set payment_method_status = ?, payment_method_update_at = ?, payment_method_update_by = ? where payment_method_id = ?",
                active, LocalDateTime.now(), actorId, id
        );
    }
}
