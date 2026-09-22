package com.sabyeguyz.clinic.databasev2.treatment;

import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2TreatmentRepository {

    public record TreatmentRecord(
            Long id,
            Long patientId,
            Long appointmentId,
            Long staffId,
            Long serviceId,
            String serviceName,
            String note,
            LocalDateTime createdAt
    ) {}

    public record ServiceOptionRecord(
            Long id,
            String code,
            String name,
            int durationMinutes,
            BigDecimal price
    ) {}

    private final JdbcTemplate jdbc;

    public DatabaseV2TreatmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceOptionRecord> findActiveServices() {
        String sql = """
                select service_id, service_code, service_name, service_price
                from service
                where service_status = true
                order by service_name asc
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ServiceOptionRecord(
                rs.getLong("service_id"),
                rs.getString("service_code"),
                rs.getString("service_name"),
                60,
                rs.getBigDecimal("service_price")
        ));
    }

    public List<TreatmentRecord> findByPatientId(Long patientId) {
        String sql = """
                select t.treatment_id, pv.patient_id, pv.appointment_id, t.treating_staff_id,
                       t.service_id, s.service_name, t.notes, t.treatment_create_at
                from treatment t
                join patient_visit pv on pv.visit_id = t.visit_id
                join service s on s.service_id = t.service_id
                where pv.patient_id = ?
                order by t.treatment_create_at desc, t.treatment_id desc
                """;
        return jdbc.query(sql, (rs, rowNum) -> new TreatmentRecord(
                rs.getLong("treatment_id"),
                rs.getLong("patient_id"),
                (Long) rs.getObject("appointment_id"),
                (Long) rs.getObject("treating_staff_id"),
                rs.getLong("service_id"),
                rs.getString("service_name"),
                rs.getString("notes"),
                rs.getObject("treatment_create_at", LocalDateTime.class)
        ), patientId);
    }

    public TreatmentRecord createTreatment(
            Long patientId,
            Long appointmentId,
            Long staffId,
            Long serviceId,
            String note,
            Long actorId
    ) {
        List<Long> patientBranches = jdbc.queryForList("select branch_id from patient where patient_id = ?", Long.class, patientId);
        if (patientBranches.isEmpty()) {
            throw new DatabaseV2NotFoundException("ไม่พบผู้ป่วย id " + patientId);
        }
        Long branchId = patientBranches.get(0);

        List<String> serviceNames = jdbc.queryForList("select service_name from service where service_id = ?", String.class, serviceId);
        if (serviceNames.isEmpty()) {
            throw new DatabaseV2NotFoundException("ไม่พบบริการ id " + serviceId);
        }
        String serviceName = serviceNames.get(0);

        if (staffId != null) {
            List<Long> staffIds = jdbc.queryForList("select staff_id from staff where staff_id = ?", Long.class, staffId);
            if (staffIds.isEmpty()) {
                throw new DatabaseV2NotFoundException("ไม่พบพนักงาน id " + staffId);
            }
        }

        Long visitId = null;
        if (appointmentId != null) {
            List<Long> apptBranches = jdbc.queryForList("select branch_id from appointment where appointment_id = ?", Long.class, appointmentId);
            if (apptBranches.isEmpty()) {
                throw new DatabaseV2NotFoundException("ไม่พบนัดหมาย id " + appointmentId);
            }
            branchId = apptBranches.get(0);

            List<Long> existingVisits = jdbc.queryForList("select visit_id from patient_visit where appointment_id = ?", Long.class, appointmentId);
            if (!existingVisits.isEmpty()) {
                visitId = existingVisits.get(0);
            } else {
                visitId = jdbc.queryForObject(
                        """
                        insert into patient_visit (
                            appointment_id, patient_id, branch_id, visit_date, visit_status, visit_create_at, visit_create_by
                        ) values (
                            ?, ?, ?, current_timestamp, 'Completed', current_timestamp, ?
                        ) returning visit_id
                        """,
                        Long.class, appointmentId, patientId, branchId, actorId
                );
            }
        } else {
            visitId = jdbc.queryForObject(
                    """
                    insert into patient_visit (
                        patient_id, branch_id, visit_date, visit_status, visit_create_at, visit_create_by
                    ) values (
                        ?, ?, current_timestamp, 'Completed', current_timestamp, ?
                    ) returning visit_id
                    """,
                    Long.class, patientId, branchId, actorId
            );
        }

        Long treatmentId = jdbc.queryForObject(
                """
                insert into treatment (
                    visit_id, service_id, treating_staff_id, notes,
                    treatment_create_at, treatment_create_by
                ) values (
                    ?, ?, ?, ?, current_timestamp, ?
                ) returning treatment_id
                """,
                Long.class, visitId, serviceId, staffId, note != null ? note.trim() : "", actorId
        );

        return new TreatmentRecord(
                treatmentId,
                patientId,
                appointmentId,
                staffId,
                serviceId,
                serviceName,
                note != null ? note.trim() : "",
                LocalDateTime.now()
        );
    }
}
