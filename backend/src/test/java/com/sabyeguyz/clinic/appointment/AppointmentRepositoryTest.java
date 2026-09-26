package com.sabyeguyz.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.therapist.Therapist;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AppointmentRepositoryTest {
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired PatientRepository patientRepository;

    private Branch branch;
    private Patient patient;
    private Therapist therapistOne;
    private Therapist therapistTwo;
    private ServiceItem service;
    private Appointment existingAppointment;

    @BeforeEach
    void setUp() {
        branch = branchRepository.findByBranchCodeAndActiveTrue("01").orElseThrow();
        patient = patientRepository.findById(registerPatient()).orElseThrow();
        therapistOne = therapistByCode("RP-T01");
        therapistTwo = therapistByCode("RP-T02");
        service = serviceByCode("PHYSIO_60");

        // นัดเดิมของ therapistOne: 09:00 - 10:00
        existingAppointment = book(therapistOne, "09:00", "10:00");
    }

    @Test
    void detectsPartialOverlap() {
        assertThat(overlaps(therapistOne, "09:30", "10:30")).isTrue();
    }

    @Test
    void detectsFullyContainedAppointment() {
        assertThat(overlaps(therapistOne, "09:15", "09:45")).isTrue();
    }

    @Test
    void detectsEnclosingAppointment() {
        assertThat(overlaps(therapistOne, "08:00", "11:00")).isTrue();
    }

    @Test
    void allowsBackToBackAppointmentAfter() {
        assertThat(overlaps(therapistOne, "10:00", "11:00")).isFalse();
    }

    @Test
    void allowsBackToBackAppointmentBefore() {
        assertThat(overlaps(therapistOne, "08:00", "09:00")).isFalse();
    }

    @Test
    void allowsSameTimeForAnotherTherapist() {
        assertThat(overlaps(therapistTwo, "09:00", "10:00")).isFalse();
    }

    @Test
    void ignoresCancelledAppointments() {
        Appointment cancelled = book(therapistTwo, "13:00", "14:00");
        cancelled.cancel("ผู้ป่วยแจ้งเลื่อน");
        appointmentRepository.saveAndFlush(cancelled);

        assertThat(overlaps(therapistTwo, "13:00", "14:00")).isFalse();
    }

    @Test
    void ignoresNoShowAppointments() {
        Appointment noShow = book(therapistTwo, "15:00", "16:00");
        noShow.markNoShow();
        appointmentRepository.saveAndFlush(noShow);

        assertThat(overlaps(therapistTwo, "15:00", "16:00")).isFalse();
    }

    @Test
    void stillDetectsRescheduledAppointments() {
        Appointment rescheduled = book(therapistTwo, "17:00", "18:00");
        rescheduled.reschedule(therapistTwo, at("18:00"), at("19:00"));
        appointmentRepository.saveAndFlush(rescheduled);

        assertThat(overlaps(therapistTwo, "18:00", "19:00")).isTrue();
    }

    @Test
    void excludingAppointmentAllowsRescheduleOntoSameSlot() {
        Appointment existing = existingAppointment;

        boolean overlapsWithSelfExcluded = appointmentRepository.existsOverlappingExcludingAppointment(
                existing.getTherapist().getId(), existing.getStartAt(), existing.getEndAt(),
                AppointmentStatus.slotReleasing(), existing.getId());

        assertThat(overlapsWithSelfExcluded).isFalse();
    }

    @Test
    void excludingAppointmentStillDetectsOtherOverlaps() {
        Appointment a = existingAppointment;
        Appointment b = book(therapistOne, "10:30", "11:30");

        boolean overlaps = appointmentRepository.existsOverlappingExcludingAppointment(
                a.getTherapist().getId(), b.getStartAt().minus(Duration.ofMinutes(15)),
                b.getStartAt().plus(Duration.ofMinutes(15)), AppointmentStatus.slotReleasing(), a.getId());

        assertThat(overlaps).isTrue();
    }

    private boolean overlaps(Therapist therapist, String start, String end) {
        return appointmentRepository.existsOverlapping(
                therapist.getId(), at(start), at(end), AppointmentStatus.slotReleasing());
    }

    private Appointment book(Therapist therapist, String start, String end) {
        return appointmentRepository.saveAndFlush(new Appointment(
                branch, patient, therapist, service, at(start), at(end), null));
    }

    private static Instant at(String time) {
        return OffsetDateTime.parse("2026-09-01T" + time + ":00+07:00").toInstant();
    }

    private Therapist therapistByCode(String code) {
        return therapistRepository.findAll().stream()
                .filter(t -> code.equals(t.getCode())).findFirst().orElseThrow();
    }

    private ServiceItem serviceByCode(String code) {
        return serviceItemRepository.findAll().stream()
                .filter(s -> code.equals(s.getCode())).findFirst().orElseThrow();
    }

    private Long registerPatient() {
        return registrationService.register(new PatientRegistrationRequest(
                "01", "appt-test", "appt-test@example.com", "password123", "นาย",
                "ทดสอบ นัดหมาย", LocalDate.of(1990, 1, 1), "ชาย", "0800000000",
                "NATIONAL_ID", "1100000000999", "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}