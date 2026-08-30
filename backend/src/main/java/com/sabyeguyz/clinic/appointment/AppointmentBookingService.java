package com.sabyeguyz.clinic.appointment;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.therapist.Therapist;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentBookingService {
    private final BranchRepository branchRepository;
    private final PatientRepository patientRepository;
    private final TherapistRepository therapistRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentBookingService(BranchRepository branchRepository,
                                     PatientRepository patientRepository,
                                     TherapistRepository therapistRepository,
                                     ServiceItemRepository serviceItemRepository,
                                     AppointmentRepository appointmentRepository) {
        this.branchRepository = branchRepository;
        this.patientRepository = patientRepository;
        this.therapistRepository = therapistRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        Branch branch = branchRepository.findByCodeAndActiveTrue(request.branchCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Therapist therapist = therapistRepository.findById(request.therapistId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพบำบัด"));
        ServiceItem service = serviceItemRepository.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));

        if (!patient.isActive()) {
            throw new IllegalArgumentException("ผู้ป่วยถูกปิดการใช้งาน");
        }
        if (!therapist.isActive()) {
            throw new IllegalArgumentException("นักกายภาพบำบัดถูกปิดการใช้งาน");
        }
        if (!service.isActive()) {
            throw new IllegalArgumentException("บริการถูกปิดการใช้งาน");
        }
        if (!therapist.getBranch().getId().equals(branch.getId())) {
            throw new IllegalArgumentException("นักกายภาพบำบัดไม่ได้สังกัดสาขานี้");
        }

        Instant startAt = request.startAt().toInstant();
        if (startAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("ไม่สามารถจองเวลาย้อนหลังได้");
        }
        Instant endAt = startAt.plus(Duration.ofMinutes(service.getDefaultDurationMinutes()));

        if (appointmentRepository.existsOverlapping(
                therapist.getId(), startAt, endAt, AppointmentStatus.CANCELLED)) {
            throw new AppointmentConflictException("นักกายภาพบำบัดมีนัดหมายในช่วงเวลานี้แล้ว");
        }

        Appointment appointment = appointmentRepository.save(
                new Appointment(branch, patient, therapist, service, startAt, endAt, request.note()));
        return AppointmentResponse.from(appointment);
    }
}