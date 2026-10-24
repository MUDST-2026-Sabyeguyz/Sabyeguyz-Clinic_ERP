package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import com.sabyeguyz.clinicv2.enums.AppointmentPatientType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actual clinic attendance (Clinic ERP Workflow Brief §4). Keeps
 * {@link Appointment}'s status in sync by delegating every transition to
 * {@link AppointmentService}, which is the single writer of that status.
 */
@Service
public class PatientVisitService {
    private final PatientVisitRepository visits;
    private final AppointmentService appointmentService;

    public PatientVisitService(PatientVisitRepository visits, AppointmentService appointmentService) {
        this.visits = visits;
        this.appointmentService = appointmentService;
    }

    @Transactional(readOnly = true)
    public List<PatientVisit> list() {
        return visits.findAll();
    }

    @Transactional(readOnly = true)
    public PatientVisit get(Long id) {
        return find(id);
    }

    @Transactional
    public PatientVisit createForService(Long appointmentId, Patient patient, Branch branch, Staff treatingStaff, Long actorId) {
        Appointment appointment = appointmentService.requireServiceAppointment(appointmentId, patient, branch);
        require(treatingStaff != null
                        && treatingStaff.getBranch() != null
                        && branch.getId().equals(
                        treatingStaff.getBranch().getId()),
                "ผู้รักษาต้องอยู่ในสาขารับบริการ");

        PatientVisit visit = new PatientVisit();
        visit.setPatient(patient);
        visit.setBranch(branch);
        visit.setAssignedStaff(appointment.getStaff());
        visit.setTreatingStaff(treatingStaff);

        if (appointment.getAppointmentPatientType() == AppointmentPatientType.CURRENT) {
            if (visits.findFirstByAppointmentIdOrderByIdDesc(appointmentId).isPresent()) {
                throw new IllegalStateException("นัดนี้มี Visit แล้ว ต้องตรวจรายการเดิมก่อน");
            }
            visit.setAppointment(appointment);
        }

        return save(visit, actorId);
    }

    /** Checks in a patient against an existing, booked Appointment. */
    @Transactional
    public PatientVisit checkInFromAppointment(Long appointmentId, Long actorId) {
        throw new IllegalStateException(
                "ให้บันทึกบริการและรับชำระ หรือใช้สิทธิ์คอร์สผ่านหน้าบริการ " + "ระบบจะสร้าง Visit เมื่อทำรายการสำเร็จ");
    }

    /** Walk-in / Changing: no prior Appointment. */
    @Transactional
    public PatientVisit checkInWalkIn(Patient patient, Branch branch, Staff assignedStaff, Long actorId) {
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        PatientVisit visit = new PatientVisit();
        visit.setPatient(patient);
        visit.setBranch(branch);
        visit.setAssignedStaff(assignedStaff);
        return save(visit, actorId);
    }

    @Transactional
    public PatientVisit startService(Long visitId, Long actorId) {
        PatientVisit visit = find(visitId);
        requireNotTerminal(visit);
        ensureTreatingStaff(visit);
        visit.setVisitStatus(VisitStatus.InService);
        touch(visit, actorId);
        if (visit.getAppointment() != null) {
            appointmentService.markInService(visit.getAppointment().getId(), actorId);
        }
        return visits.save(visit);
    }

    /** Called after payment or course usage succeeds. */
    @Transactional
    public PatientVisit completeForService(Long visitId, Long appointmentId, Long actorId) {
        PatientVisit visit = find(visitId);
        requireNotTerminal(visit);
        ensureTreatingStaff(visit);

        Appointment appointment = appointmentService.requireServiceAppointment(
                        appointmentId, visit.getPatient(), visit.getBranch());

        if (appointment.getAppointmentPatientType() == AppointmentPatientType.CURRENT) {
            require(visit.getAppointment() != null
                            && appointmentId.equals(
                            visit.getAppointment().getId()),
                    "Visit ต้องเชื่อมกับนัด CURRENT ที่รับบริการ");
        } else {
            require(visit.getAppointment() == null,
                    "Visit จากนัด NEW ต้องไม่เชื่อมนัดย้อนหลัง");
        }

        Instant completedAt = Instant.now();
        visit.setVisitStatus(VisitStatus.Completed);
        visit.setVisitDate(completedAt);
        visit.setCheckoutTime(completedAt);
        touch(visit, actorId);

        appointmentService.confirmAfterService(appointmentId, actorId);
        return visits.save(visit);
    }

    @Transactional
    public PatientVisit complete(Long visitId, Long actorId) {
        PatientVisit visit = find(visitId);
        requireNotTerminal(visit);
        ensureTreatingStaff(visit);
        visit.setVisitStatus(VisitStatus.Completed);
        visit.setCheckoutTime(Instant.now());
        touch(visit, actorId);
        if (visit.getAppointment() != null) {
            appointmentService.markCompleted(visit.getAppointment().getId(), actorId);
        }
        return visits.save(visit);
    }

    @Transactional
    public PatientVisit cancel(Long visitId, Long actorId) {
        PatientVisit visit = find(visitId);
        requireNotTerminal(visit);
        visit.setVisitStatus(VisitStatus.Cancelled);
        touch(visit, actorId);
        return visits.save(visit);
    }

    /** A started visit needs the PT who treats it: the one set on it, else the assigned PT. */
    private static void ensureTreatingStaff(PatientVisit visit) {
        if (visit.getTreatingStaff() == null) {
            visit.setTreatingStaff(visit.getAssignedStaff());
        }
        if (visit.getTreatingStaff() == null) {
            throw new IllegalArgumentException("ต้องระบุนักกายภาพผู้รักษาก่อนเริ่มหรือจบการรักษา");
        }
    }

    private PatientVisit save(PatientVisit visit, Long actorId) {
        visit.setVisitStatus(VisitStatus.Arrived);
        visit.setVisitDate(Instant.now());
        visit.setVisitCreateAt(Instant.now());
        visit.setVisitCreateBy(actorId);
        return visits.save(visit);
    }

    private void touch(PatientVisit visit, Long actorId) {
        visit.setVisitUpdateAt(Instant.now());
        visit.setVisitUpdateBy(actorId);
    }

    private static void requireNotTerminal(PatientVisit visit) {
        VisitStatus status = visit.getVisitStatus();
        if (status == VisitStatus.Completed || status == VisitStatus.Cancelled) {
            throw new IllegalArgumentException("การมารับบริการนี้จบสถานะแล้ว ไม่สามารถแก้ไขได้อีก");
        }
    }

    private PatientVisit find(Long id) {
        return visits.findById(id).orElseThrow(PatientVisitNotFoundException::new);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
