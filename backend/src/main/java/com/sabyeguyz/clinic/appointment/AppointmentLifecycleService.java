package com.sabyeguyz.clinic.appointment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentLifecycleService {
    private final AppointmentRepository appointmentRepository;

    public AppointmentLifecycleService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public AppointmentResponse checkIn(long id) {
        Appointment appointment = require(id);
        appointment.checkIn();
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse complete(long id) {
        Appointment appointment = require(id);
        appointment.complete();
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(long id, AppointmentCancelRequest request) {
        Appointment appointment = require(id);
        appointment.cancel(request.reason());
        return AppointmentResponse.from(appointment);
    }

    private Appointment require(long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
    }
}