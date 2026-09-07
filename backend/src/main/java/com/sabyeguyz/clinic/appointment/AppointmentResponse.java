package com.sabyeguyz.clinic.appointment;

import java.time.Instant;

public record AppointmentResponse(
        Long id,
        String branchCode,
        PatientSummary patient,
        TherapistSummary therapist,
        ServiceSummary service,
        Instant startAt,
        Instant endAt,
        AppointmentStatus status,
        String note,
        String cancelReason
) {
    public record PatientSummary(Long id, String hn, String fullName) {}
    public record TherapistSummary(Long id, String fullName) {}
    public record ServiceSummary(Long id, String name, int durationMinutes) {}

    static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getBranch().getCode(),
                new PatientSummary(
                        appointment.getPatient().getId(),
                        appointment.getPatient().getHn(),
                        appointment.getPatient().getFullName()),
                new TherapistSummary(
                        appointment.getTherapist().getId(),
                        appointment.getTherapist().getFullName()),
                new ServiceSummary(
                        appointment.getService().getId(),
                        appointment.getService().getName(),
                        appointment.getService().getDefaultDurationMinutes()),
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getStatus(),
                appointment.getNote(),
                appointment.getCancelReason());
    }
}