package com.sabyeguyz.clinic.databasev2.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2AppointmentRepository {

    public record AppointmentModel(
            Long id,
            Long patientId,
            String patientHn,
            String patientFullName,
            Long branchId,
            String branchCode,
            Long staffId,
            String staffFullName,
            Long serviceId,
            String serviceName,
            int durationMinutes,
            Long roomId,
            String roomName,
            LocalDate date,
            LocalTime time,
            String status,
            LocalDateTime createdAt,
            String cancelReason,
            String note
    ) {}

    public record StatusHistoryModel(
            Long id,
            Long appointmentId,
            String fromStatus,
            String toStatus,
            Long changedBy,
            LocalDateTime changedAt,
            String reason
    ) {}

    private final JdbcTemplate jdbc;

    private static final String SELECT_APPOINTMENT = """
            select a.appointment_id, a.patient_id, p.hn,
                   concat(p.patient_fname, case when p.patient_lname is not null and p.patient_lname <> '-' then concat(' ', p.patient_lname) else '' end) as patient_name,
                   a.branch_id, b.branch_code, a.staff_id,
                   concat(s.staff_fname, case when s.staff_lname is not null and s.staff_lname <> '-' then concat(' ', s.staff_lname) else '' end) as staff_name,
                   a.service_id, sv.service_name,
                   a.room_id, r.room_name,
                   a.appointment_date, a.appointment_time,
                   cast(a.appointment_status as varchar) as status,
                   a.appointment_create_at,
                   (select h.reason from appointment_status_history h where h.appointment_id = a.appointment_id and h.to_status = 'Cancelled' order by h.history_id desc limit 1) as cancel_reason,
                   (select h.reason from appointment_status_history h where h.appointment_id = a.appointment_id and h.from_status is null order by h.history_id asc limit 1) as note
            from appointment a
            join patient p on p.patient_id = a.patient_id
            join branch b on b.branch_id = a.branch_id
            join staff s on s.staff_id = a.staff_id
            join service sv on sv.service_id = a.service_id
            left join room r on r.room_id = a.room_id
            """;

    private static final RowMapper<AppointmentModel> ROW_MAPPER = (rs, rowNum) -> new AppointmentModel(
            rs.getLong("appointment_id"),
            rs.getLong("patient_id"),
            rs.getString("hn"),
            rs.getString("patient_name"),
            rs.getLong("branch_id"),
            rs.getString("branch_code"),
            rs.getLong("staff_id"),
            rs.getString("staff_name"),
            rs.getLong("service_id"),
            rs.getString("service_name"),
            60,
            (Long) rs.getObject("room_id"),
            rs.getString("room_name"),
            rs.getObject("appointment_date", LocalDate.class),
            rs.getObject("appointment_time", LocalTime.class),
            rs.getString("status"),
            rs.getObject("appointment_create_at", LocalDateTime.class),
            rs.getString("cancel_reason"),
            rs.getString("note")
    );

    public DatabaseV2AppointmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppointmentModel> findById(Long id) {
        return jdbc.query(SELECT_APPOINTMENT + " where a.appointment_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public List<AppointmentModel> search(String branchCode, LocalDate date, Long staffId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT_APPOINTMENT);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (branchCode != null && !branchCode.isBlank()) {
            conditions.add("b.branch_code = ?");
            params.add(branchCode.trim().toUpperCase());
        }
        if (date != null) {
            conditions.add("a.appointment_date = ?");
            params.add(date);
        }
        if (staffId != null) {
            conditions.add("a.staff_id = ?");
            params.add(staffId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("a.appointment_status = cast(? as appointment_status_enum)");
            params.add(normalizeStatus(status));
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }

        sql.append(" order by a.appointment_date, a.appointment_time limit ? offset ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public int countSearch(String branchCode, LocalDate date, Long staffId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from appointment a join branch b on b.branch_id = a.branch_id");
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (branchCode != null && !branchCode.isBlank()) {
            conditions.add("b.branch_code = ?");
            params.add(branchCode.trim().toUpperCase());
        }
        if (date != null) {
            conditions.add("a.appointment_date = ?");
            params.add(date);
        }
        if (staffId != null) {
            conditions.add("a.staff_id = ?");
            params.add(staffId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("a.appointment_status = cast(? as appointment_status_enum)");
            params.add(normalizeStatus(status));
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }

        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public boolean hasConflict(Long staffId, LocalDate date, LocalTime time, Long excludeId) {
        String sql = """
                select count(*) from appointment
                where staff_id = ? and appointment_date = ? and appointment_time = ?
                  and appointment_status in ('Confirmed', 'Arrived', 'InService', 'Rescheduled')
                """;
        if (excludeId != null) {
            sql += " and appointment_id <> " + excludeId;
        }
        Integer count = jdbc.queryForObject(sql, Integer.class, staffId, date, time);
        return count != null && count > 0;
    }

    public AppointmentModel create(
            Long patientId, Long branchId, Long staffId, Long serviceId, Long roomId,
            LocalDate date, LocalTime time, String note, Long actorId
    ) {
        Long id = jdbc.queryForObject(
                """
                insert into appointment (
                    patient_id, branch_id, staff_id, service_id, room_id,
                    appointment_date, appointment_time, appointment_status,
                    appointment_create_at, appointment_create_by
                ) values (
                    ?, ?, ?, ?, ?,
                    ?, ?, 'Confirmed',
                    current_timestamp, ?
                ) returning appointment_id
                """,
                Long.class,
                patientId, branchId, staffId, serviceId, roomId,
                date, time, actorId
        );

        String reason = (note != null && !note.isBlank()) ? note.trim() : "Initial booking";
        recordHistory(id, null, "Confirmed", reason, actorId);
        return findById(id).orElseThrow();
    }

    public void updateStatus(Long id, String fromStatus, String toStatus, String reason, Long actorId) {
        jdbc.update(
                """
                update appointment set
                    appointment_status = cast(? as appointment_status_enum),
                    appointment_update_at = current_timestamp,
                    appointment_update_by = ?
                where appointment_id = ?
                """,
                toStatus, actorId, id
        );
        recordHistory(id, fromStatus, toStatus, reason, actorId);
    }

    public void reschedule(Long id, Long newStaffId, LocalDate newDate, LocalTime newTime, String oldStatus, String reason, Long actorId) {
        jdbc.update(
                """
                update appointment set
                    staff_id = ?, appointment_date = ?, appointment_time = ?,
                    appointment_status = 'Rescheduled',
                    appointment_update_at = current_timestamp,
                    appointment_update_by = ?
                where appointment_id = ?
                """,
                newStaffId, newDate, newTime, actorId, id
        );
        recordHistory(id, oldStatus, "Rescheduled", reason, actorId);
    }

    public void recordHistory(Long appointmentId, String fromStatus, String toStatus, String reason, Long actorId) {
        jdbc.update(
                """
                insert into appointment_status_history (
                    appointment_id, from_status, to_status, changed_by, changed_at, reason
                ) values (
                    ?, cast(nullif(?, '') as appointment_status_enum), cast(? as appointment_status_enum),
                    ?, current_timestamp, ?
                )
                """,
                appointmentId, fromStatus, toStatus, actorId, reason
        );
    }

    public Long createPatientVisit(Long appointmentId, Long patientId, Long branchId, Long staffId, Long actorId) {
        return jdbc.queryForObject(
                """
                insert into patient_visit (
                    appointment_id, patient_id, branch_id, assigned_staff_id,
                    visit_date, visit_status, visit_create_at, visit_create_by
                ) values (
                    ?, ?, ?, ?,
                    current_timestamp, 'Arrived', current_timestamp, ?
                ) returning visit_id
                """,
                Long.class, appointmentId, patientId, branchId, staffId, actorId
        );
    }

    public void updateVisitStatus(Long appointmentId, String status, Long actorId) {
        if ("Completed".equals(status)) {
            jdbc.update(
                    """
                    update patient_visit set
                        visit_status = cast(? as visit_status_enum),
                        completed_at = current_timestamp,
                        visit_update_at = current_timestamp,
                        visit_update_by = ?
                    where appointment_id = ?
                    """,
                    status, actorId, appointmentId
            );
        } else {
            jdbc.update(
                    """
                    update patient_visit set
                        visit_status = cast(? as visit_status_enum),
                        visit_update_at = current_timestamp,
                        visit_update_by = ?
                    where appointment_id = ?
                    """,
                    status, actorId, appointmentId
            );
        }
    }

    public Optional<Long> findVisitIdByAppointmentId(Long appointmentId) {
        return jdbc.query(
                "select visit_id from patient_visit where appointment_id = ?",
                (rs, rowNum) -> rs.getLong("visit_id"),
                appointmentId
        ).stream().findFirst();
    }

    public static String normalizeStatus(String s) {
        if (s == null) return "Confirmed";
        String clean = s.trim().toUpperCase();
        return switch (clean) {
            case "CONFIRMED" -> "Confirmed";
            case "ARRIVED" -> "Arrived";
            case "IN_SERVICE", "INSERVICE" -> "InService";
            case "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            case "RESCHEDULED" -> "Rescheduled";
            case "NO_SHOW", "NOSHOW" -> "NoShow";
            default -> s;
        };
    }
}
