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
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentBookingService {
    private static final String OVERLAP_CONSTRAINT = "excl_appointments_therapist_overlap";
    private static final String OVERLAP_MESSAGE = "นักกายภาพบำบัดมีนัดหมายในช่วงเวลานี้แล้ว";

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
        Branch branch = branchRepository.findByBranchCodeAndActiveTrue(request.branchCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Therapist therapist = therapistRepository.findByIdForUpdate(request.therapistId())
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
                therapist.getId(), startAt, endAt, AppointmentStatus.slotReleasing())) {
            throw new AppointmentConflictException(OVERLAP_MESSAGE);
        }

        Appointment appointment = new Appointment(branch, patient, therapist, service, startAt, endAt, request.note());
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException exception) {
            throw translateOverlapViolation(exception);
        }
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse reschedule(long id, AppointmentRescheduleRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        Long targetTherapistId = request.therapistId() != null
                ? request.therapistId()
                : appointment.getTherapist().getId();
        Therapist therapist = therapistRepository.findByIdForUpdate(targetTherapistId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพบำบัด"));

        if (!therapist.isActive()) {
            throw new IllegalArgumentException("นักกายภาพบำบัดถูกปิดการใช้งาน");
        }
        if (!therapist.getBranch().getId().equals(appointment.getBranch().getId())) {
            throw new IllegalArgumentException("นักกายภาพบำบัดไม่ได้สังกัดสาขานี้");
        }

        Instant startAt = request.startAt().toInstant();
        if (startAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("ไม่สามารถเลื่อนนัดไปเวลาย้อนหลังได้");
        }
        Duration duration = Duration.between(appointment.getStartAt(), appointment.getEndAt());
        Instant endAt = startAt.plus(duration);

        if (appointmentRepository.existsOverlappingExcludingAppointment(
                therapist.getId(), startAt, endAt, AppointmentStatus.slotReleasing(), appointment.getId())) {
            throw new AppointmentConflictException(OVERLAP_MESSAGE);
        }

        appointment.reschedule(therapist, startAt, endAt);
        try {
            appointmentRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateOverlapViolation(exception);
        }
        return AppointmentResponse.from(appointment);
    }

    private RuntimeException translateOverlapViolation(DataIntegrityViolationException exception) {
        String cause = NestedExceptionUtils.getMostSpecificCause(exception).getMessage();
        if (cause != null && cause.contains(OVERLAP_CONSTRAINT)) {
            return new AppointmentConflictException(OVERLAP_MESSAGE);
        }
        return exception;
    }
}