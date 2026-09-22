package com.sabyeguyz.clinic.databasev2.staff;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2StaffRepository {

    public record StaffModel(
            Long id,
            String code,
            String firstName,
            String lastName,
            String position,
            Long branchId,
            String branchCode,
            String branchName,
            boolean active,
            LocalDate hireDate,
            LocalDate terminationDate,
            String terminationPolicy,
            Long userId,
            String username,
            String role
    ) {
        public String fullName() {
            if (lastName == null || lastName.isBlank() || "-".equals(lastName)) {
                return firstName;
            }
            return firstName + " " + lastName;
        }
    }

    private final JdbcTemplate jdbc;

    private static final String SELECT_STAFF = """
            select s.staff_id, s.staff_code, s.staff_fname, s.staff_lname,
                   cast(s.position as varchar) as position, s.branch_id,
                   b.branch_code, b.branch_name, s.staff_status, s.hire_date,
                   s.termination_date,
                   cast(s.commission_after_termination_policy as varchar) as termination_policy,
                   u.user_id, u.username,
                   (select r.role_name from role r join user_role ur on ur.role_id = r.role_id where ur.user_id = u.user_id limit 1) as role_name
            from staff s
            left join branch b on b.branch_id = s.branch_id
            left join app_user u on u.staff_id = s.staff_id
            """;

    private static final RowMapper<StaffModel> ROW_MAPPER = (rs, rowNum) -> new StaffModel(
            rs.getLong("staff_id"),
            rs.getString("staff_code"),
            rs.getString("staff_fname"),
            rs.getString("staff_lname"),
            rs.getString("position"),
            rs.getLong("branch_id"),
            rs.getString("branch_code"),
            rs.getString("branch_name"),
            rs.getBoolean("staff_status"),
            rs.getObject("hire_date", LocalDate.class),
            rs.getObject("termination_date", LocalDate.class),
            rs.getString("termination_policy"),
            rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
            rs.getString("username"),
            rs.getString("role_name")
    );

    public DatabaseV2StaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StaffModel> findAll() {
        return jdbc.query(SELECT_STAFF + " order by s.staff_fname, s.staff_lname", ROW_MAPPER);
    }

    public List<StaffModel> findByPosition(String position) {
        return jdbc.query(SELECT_STAFF + " where s.position = cast(? as staff_position_enum) order by s.staff_fname",
                ROW_MAPPER, position);
    }

    public List<StaffModel> findActiveTherapists(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return jdbc.query(SELECT_STAFF + " where s.position = 'Therapist' and s.staff_status = true order by s.staff_fname",
                    ROW_MAPPER);
        }
        return jdbc.query(SELECT_STAFF + " where s.position = 'Therapist' and s.staff_status = true and b.branch_code = ? order by s.staff_fname",
                ROW_MAPPER, branchCode.trim());
    }

    public Optional<StaffModel> findById(Long id) {
        return jdbc.query(SELECT_STAFF + " where s.staff_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<StaffModel> findByCode(String code) {
        return jdbc.query(SELECT_STAFF + " where s.staff_code = ?", ROW_MAPPER, code.trim()).stream().findFirst();
    }

    public Optional<StaffModel> findByUserId(Long userId) {
        return jdbc.query(SELECT_STAFF + " where u.user_id = ?", ROW_MAPPER, userId).stream().findFirst();
    }

    public Optional<StaffModel> findFirstActive(Long branchId) {
        if (branchId != null) {
            List<StaffModel> list = jdbc.query(
                    SELECT_STAFF + " where s.staff_status = true and s.branch_id = ? order by s.staff_id limit 1",
                    ROW_MAPPER, branchId
            );
            if (!list.isEmpty()) return Optional.of(list.get(0));
        }
        List<StaffModel> list = jdbc.query(
                SELECT_STAFF + " where s.staff_status = true order by s.staff_id limit 1",
                ROW_MAPPER
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public StaffModel create(String code, String fname, String lname, String position, Long branchId,
                             LocalDate hireDate, Long actorId) {
        Long id = jdbc.queryForObject(
                """
                insert into staff (
                    staff_code, staff_fname, staff_lname, position, branch_id,
                    staff_status, hire_date, staff_create_at, staff_create_by
                ) values (
                    ?, ?, ?, cast(? as staff_position_enum), ?,
                    true, ?, ?, ?
                ) returning staff_id
                """,
                Long.class,
                code, fname, lname, position, branchId,
                hireDate != null ? hireDate : LocalDate.now(), LocalDateTime.now(), actorId
        );
        return findById(id).orElseThrow();
    }

    public StaffModel update(Long id, String code, String fname, String lname, String position, Long branchId, Long actorId) {
        jdbc.update(
                """
                update staff set
                    staff_code = ?, staff_fname = ?, staff_lname = ?,
                    position = cast(? as staff_position_enum), branch_id = ?,
                    staff_update_at = ?, staff_update_by = ?
                where staff_id = ?
                """,
                code, fname, lname, position, branchId, LocalDateTime.now(), actorId, id
        );
        return findById(id).orElseThrow();
    }

    public void setStatus(Long id, boolean active, Long actorId) {
        jdbc.update(
                "update staff set staff_status = ?, staff_update_at = ?, staff_update_by = ? where staff_id = ?",
                active, LocalDateTime.now(), actorId, id
        );
    }
}
