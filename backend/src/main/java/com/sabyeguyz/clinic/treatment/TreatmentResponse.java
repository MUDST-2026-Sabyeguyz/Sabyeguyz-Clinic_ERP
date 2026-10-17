package com.sabyeguyz.clinic.treatment;
import java.time.Instant;
public record TreatmentResponse(Long id, Long patientId, Long appointmentId, Long staffId,
        Long branchId, String service, String note, Instant createdAt) {
    static TreatmentResponse from(Treatment t) {
        return new TreatmentResponse(t.getId(),t.getPatient().getId(),
            t.getAppointment()==null?null:t.getAppointment().getId(),
            t.getStaff()==null?null:t.getStaff().getId(),
            t.getAppointment()==null?null:t.getAppointment().getBranch().getId(),
            t.getService(),t.getNote(),t.getCreatedAt());
    }
}
