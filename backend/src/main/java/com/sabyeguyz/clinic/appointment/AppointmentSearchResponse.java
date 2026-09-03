package com.sabyeguyz.clinic.appointment;

import java.util.List;
import org.springframework.data.domain.Page;

public record AppointmentSearchResponse(
        List<AppointmentResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    static AppointmentSearchResponse from(Page<Appointment> appointments) {
        return new AppointmentSearchResponse(
                appointments.getContent().stream().map(AppointmentResponse::from).toList(),
                appointments.getNumber(), appointments.getSize(),
                appointments.getTotalElements(), appointments.getTotalPages());
    }
}