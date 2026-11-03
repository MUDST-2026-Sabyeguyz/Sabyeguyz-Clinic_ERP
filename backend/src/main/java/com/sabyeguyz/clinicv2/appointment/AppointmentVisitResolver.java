package com.sabyeguyz.clinicv2.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.audit.AuditLog;
import com.sabyeguyz.clinicv2.audit.AuditLogRepository;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.enums.AppointmentPatientType;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentVisitResolver {
    private static final ObjectMapper JSON = new ObjectMapper();

    // Internal result only; the HTTP API will return its own response.
    public record Resolution(
            Appointment appointment,
            PatientVisit visit,
            String state,
            String reason) {}

    private final AppointmentService appointments;
    private final PatientVisitRepository visits;
    private final AuditLogRepository audits;
    private final BranchAccessPolicy branchAccess;

    public AppointmentVisitResolver(
            AppointmentService appointments,
            PatientVisitRepository visits,
            AuditLogRepository audits,
            BranchAccessPolicy branchAccess) {
        this.appointments = appointments;
        this.visits = visits;
        this.audits = audits;
        this.branchAccess = branchAccess;
    }

    public Resolution resolve(Long appointmentId) {
        Appointment appointment = appointments.get(appointmentId);
        branchAccess.requireAccess(appointment.getBranch());

        Map<Long, PatientVisit> candidates = new TreeMap<>();
        var linkedVisits =
                visits.findByAppointmentIdOrderByIdAsc(appointmentId);

        if (appointment.getAppointmentPatientType()
                == AppointmentPatientType.NEW && !linkedVisits.isEmpty()) {
            return review(appointment, "นัด NEW มี Visit ผูกย้อนหลัง ต้องตรวจสอบ");
        }

        linkedVisits.forEach(visit -> candidates.put(visit.getId(), visit));

        for (AuditLog entry :
                audits.findByTableNameAndRecordIdAndActionOrderByIdAsc(
                        "appointment",
                        String.valueOf(appointmentId),
                        AuditAction.UPDATE)) {
            if (entry.getNewValue() == null) {
                continue;
            }

            JsonNode value;
            try {
                value = JSON.readTree(entry.getNewValue());
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                return review(appointment, "ข้อมูล audit อ่านไม่ได้ ต้องตรวจสอบ");
            }

            if (value == null
                    || !"ServiceCompleted".equals(value.path("event").asText())) {
                continue;
            }

            Long visitId = positiveId(value.path("visitId"));
            Long patientId = positiveId(value.path("patientId"));
            Long branchId = positiveId(value.path("branchId"));

            if (visitId == null || patientId == null || branchId == null
                    || !"Confirmed".equals(value.path("status").asText())) {
                return review(appointment, "หลักฐานรับบริการมีข้อมูลไม่ครบ");
            }

            PatientVisit visit = visits.findById(visitId).orElse(null);
            if (visit == null
                    || !branchId.equals(appointment.getBranch().getId())
                    || !branchId.equals(visit.getBranch().getId())
                    || !patientId.equals(visit.getPatient().getId())) {
                return review(appointment, "หลักฐานรับบริการไม่ตรงกับ Visit");
            }

            candidates.put(visitId, visit);
        }

        if (candidates.isEmpty()) {
            if (appointment.getAppointmentStatus()
                    == AppointmentStatus.Confirmed) {
                return review(appointment, "นัดยืนยันแล้วแต่ไม่พบหลักฐาน Visit");
            }

            return new Resolution(
                    appointment, null, "NO_LINKED_RECORD",
                    "ไม่พบหลักฐานเชื่อม ไม่ใช่ข้อยืนยันว่าไม่เคยมีรายการ");
        }

        if (candidates.size() != 1) {
            return review(appointment, "พบบันทึกเชื่อมหลาย Visit ต้องตรวจสอบ");
        }

        PatientVisit visit = candidates.values().iterator().next();

        if (!appointment.getBranch().getId().equals(visit.getBranch().getId())
                || visit.getVisitStatus() != VisitStatus.Completed) {
            return review(appointment, "Visit ไม่ตรงสาขาหรือยังไม่สำเร็จ");
        }

        if (appointment.getAppointmentPatientType()
                == AppointmentPatientType.CURRENT) {
            if (visit.getAppointment() == null
                    || !appointmentId.equals(visit.getAppointment().getId())
                    || appointment.getPatient() == null
                    || !appointment.getPatient().getId()
                    .equals(visit.getPatient().getId())) {
                return review(appointment, "Visit ไม่ตรงกับนัด CURRENT");
            }
        } else if (visit.getAppointment() != null
                || appointment.getPatient() != null) {
            return review(appointment, "นัด NEW ต้องไม่ถูกผูกย้อนหลัง");
        }

        return new Resolution(appointment, visit, "FOUND", null);
    }

    private static Long positiveId(JsonNode value) {
        return value.isIntegralNumber()
                && value.canConvertToLong()
                && value.longValue() > 0
                ? value.longValue()
                : null;
    }

    private static Resolution review(
            Appointment appointment, String reason) {
        return new Resolution(
                appointment, null, "REVIEW_REQUIRED", reason);
    }
}