package com.sabyeguyz.clinic.treatment;

import com.sabyeguyz.clinic.appointment.Appointment;
import com.sabyeguyz.clinic.appointment.AppointmentRepository;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.staff.StaffRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/treatments")
@Profile("!database-v2")
public class TreatmentController {
    private final TreatmentRepository treatments;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final StaffRepository staff;
    private final ServiceItemRepository services;
    public TreatmentController(TreatmentRepository treatments, PatientRepository patients, AppointmentRepository appointments, StaffRepository staff, ServiceItemRepository services) {
        this.treatments = treatments; this.patients = patients; this.appointments = appointments; this.staff = staff; this.services = services;
    }
    public record Request(@NotNull Long patientId, Long appointmentId, Long staffId, @NotNull Long serviceId, @NotBlank String note) {}
    public record Response(Long id, Long patientId, Long appointmentId, Long staffId, Long serviceId, String service, String note) {}
    public record ServiceResponse(Long id, String code, String name, int durationMinutes, java.math.BigDecimal price) {}
    @GetMapping("/services") List<ServiceResponse> serviceList() { return services.findByActiveTrueOrderByName().stream().map(s -> new ServiceResponse(s.getId(), s.getCode(), s.getName(), s.getDefaultDurationMinutes(), s.getPrice())).toList(); }
    @GetMapping("/patients/{patientId}") List<Response> list(@PathVariable Long patientId) { return treatments.findByPatientIdOrderByCreatedAtDesc(patientId).stream().map(this::response).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Response create(@Valid @RequestBody Request request) {
        Patient patient = patients.findById(request.patientId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Appointment appointment = request.appointmentId() == null ? null : appointments.findById(request.appointmentId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบนัดหมาย"));
        Staff therapist = request.staffId() == null ? null : staff.findById(request.staffId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงาน"));
        ServiceItem service = services.findById(request.serviceId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        return response(treatments.save(new Treatment(patient, appointment, therapist, service.getName(), request.note().trim())));
    }
    private Response response(Treatment t) { return new Response(t.getId(), t.getPatient().getId(), t.getAppointment() == null ? null : t.getAppointment().getId(), t.getStaff() == null ? null : t.getStaff().getId(), null, t.getService(), t.getNote()); }
}
