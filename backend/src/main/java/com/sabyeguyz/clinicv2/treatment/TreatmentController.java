package com.sabyeguyz.clinicv2.treatment;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.appointment.PatientVisitRepository;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's treatmentApi (/api/treatments). v2's
 * Treatment always belongs to a {@link PatientVisit}, unlike the old schema
 * where a Treatment linked directly and optionally to an Appointment - so
 * unlike the old API, {@code appointmentId} here is required and must
 * already have a checked-in visit (walk-in treatment recording without a
 * prior check-in isn't supported, since PatientVisit.branch has no source
 * to come from at this endpoint).
 */
@Transactional
@RestController
@RequestMapping("/api/treatments")
public class TreatmentController {
    private final TreatmentRepository treatments;
    private final PatientRepository patients;
    private final PatientVisitRepository visits;
    private final StaffRepository staff;
    private final ServiceRepository services;
    private final CurrentActor actor;

    public TreatmentController(TreatmentRepository treatments, PatientRepository patients,
                                PatientVisitRepository visits, StaffRepository staff, ServiceRepository services,
                                CurrentActor actor) {
        this.treatments = treatments;
        this.patients = patients;
        this.visits = visits;
        this.staff = staff;
        this.services = services;
        this.actor = actor;
    }

    public record Request(Long patientId, Long appointmentId, Long staffId, Long serviceId, String note) {}

    public record Response(Long id, Long patientId, Long appointmentId, Long staffId, Long serviceId,
                            String service, String note) {}

    public record ServiceOption(Long id, String code, String name, int durationMinutes, BigDecimal price) {}

    @GetMapping("/services")
    public List<ServiceOption> serviceList() {
        return services.findByServiceStatusTrueOrderByServiceName().stream()
                .filter(s -> s.getServiceType() == ServiceType.SingleVisit)
                .map(s -> new ServiceOption(s.getId(), s.getServiceCode(), s.getServiceName(), 0, s.getServicePrice()))
                .toList();
    }

    @GetMapping("/patients/{patientId}")
    public List<Response> listByPatient(@PathVariable Long patientId) {
        return treatments.findByVisit_Patient_IdOrderByTreatmentCreateAtDesc(patientId).stream()
                .map(TreatmentController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody Request request) {
        if (request.patientId() == null) throw new IllegalArgumentException("ต้องระบุผู้ป่วย");
        if (request.appointmentId() == null) {
            throw new IllegalArgumentException("ต้องระบุนัดหมายที่เช็คอินแล้ว (ยังไม่รองรับ Walk-in ผ่าน API นี้)");
        }
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        PatientVisit visit = visits.findFirstByAppointmentIdOrderByIdDesc(request.appointmentId())
                .orElseThrow(() -> new IllegalArgumentException("ยังไม่มีการเช็คอินสำหรับนัดหมายนี้"));
        if (!visit.getPatient().getId().equals(patient.getId())) {
            throw new IllegalArgumentException("ผู้ป่วยไม่ตรงกับการเช็คอินของนัดหมายนี้");
        }
        Service service = services.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        Staff treatingStaff = request.staffId() == null ? null : staff.findById(request.staffId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงาน"));

        Treatment treatment = new Treatment();
        treatment.setVisit(visit);
        treatment.setService(service);
        treatment.setTreatingStaff(treatingStaff);
        treatment.setNotes(request.note() == null ? null : request.note().trim());
        treatment.setTreatmentCreateAt(Instant.now());
        treatment.setTreatmentCreateBy(actor.currentId());
        return toResponse(treatments.save(treatment));
    }

    private static Response toResponse(Treatment t) {
        PatientVisit visit = t.getVisit();
        Staff treatingStaff = t.getTreatingStaff();
        return new Response(t.getId(), visit.getPatient().getId(),
                visit.getAppointment() == null ? null : visit.getAppointment().getId(),
                treatingStaff == null ? null : treatingStaff.getId(), t.getService().getId(),
                t.getService().getServiceName(), t.getNotes());
    }
}
