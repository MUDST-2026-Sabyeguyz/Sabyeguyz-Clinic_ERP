package com.sabyeguyz.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AppointmentRescheduleServiceTest {
    @Autowired AppointmentBookingService bookingService;
    @Autowired AppointmentLifecycleService lifecycleService;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired EntityManager entityManager;

    private Long patientId;
    private Long therapistId;
    private Long otherTherapistId;
    private Long serviceId;
    private OffsetDateTime nineAmTomorrow;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        therapistId = therapistRepository.findAll().stream()
                .filter(t -> "RP-T01".equals(t.getCode())).findFirst().orElseThrow().getId();
        otherTherapistId = therapistRepository.findAll().stream()
                .filter(t -> "RP-T02".equals(t.getCode())).findFirst().orElseThrow().getId();
        serviceId = serviceItemRepository.findAll().stream()
                .filter(s -> "PHYSIO_60".equals(s.getCode())).findFirst().orElseThrow().getId();
        nineAmTomorrow = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void reschedulesToNewTimeKeepingSameDuration() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));

        AppointmentResponse rescheduled = bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), null));

        assertThat(rescheduled.startAt()).isEqualTo(nineAmTomorrow.plusHours(3).toInstant());
        assertThat(rescheduled.endAt()).isEqualTo(rescheduled.startAt().plus(Duration.ofMinutes(60)));
        assertThat(rescheduled.status()).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    void reschedulesToDifferentTherapistInSameBranch() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));

        AppointmentResponse rescheduled = bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), otherTherapistId));

        assertThat(rescheduled.therapist().id()).isEqualTo(otherTherapistId);
    }

    @Test
    void keepsOriginalTherapistWhenNotProvided() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));

        AppointmentResponse rescheduled = bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), null));

        assertThat(rescheduled.therapist().id()).isEqualTo(therapistId);
    }

    @Test
    void allowsReschedulingOntoItsOwnOriginalSlot() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));

        AppointmentResponse rescheduled = bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow, null));

        assertThat(rescheduled.startAt()).isEqualTo(nineAmTomorrow.toInstant());
    }

    @Test
    void rejectsRescheduleOfArrivedAppointment() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));
        lifecycleService.checkIn(original.id());

        assertThatThrownBy(() -> bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), null)))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("ARRIVED");
    }

    @Test
    void rejectsRescheduleOntoOverlappingSlot() {
        AppointmentResponse first = bookingService.create(request(nineAmTomorrow));
        bookingService.create(request(nineAmTomorrow.plusHours(2)));

        assertThatThrownBy(() -> bookingService.reschedule(
                first.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(2).plusMinutes(15), null)))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("นัดหมายในช่วงเวลานี้แล้ว");
    }

    @Test
    void allowsReschedulingBackToBackWithAnotherAppointment() {
        AppointmentResponse first = bookingService.create(request(nineAmTomorrow));
        bookingService.create(request(nineAmTomorrow.plusHours(2)));

        AppointmentResponse rescheduled = bookingService.reschedule(
                first.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(1), null));

        assertThat(rescheduled.endAt()).isEqualTo(nineAmTomorrow.plusHours(2).toInstant());
    }

    @Test
    void rejectsRescheduleToPastTime() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));

        assertThatThrownBy(() -> bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.minusDays(2), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ย้อนหลัง");
    }

    @Test
    void rejectsInactiveTherapist() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));
        entityManager.createNativeQuery("UPDATE therapists SET active = false WHERE code = 'RP-T02'")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), otherTherapistId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("นักกายภาพบำบัดถูกปิดการใช้งาน");
    }

    @Test
    void rejectsTherapistFromAnotherBranch() {
        AppointmentResponse original = bookingService.create(request(nineAmTomorrow));
        entityManager.createNativeQuery(
                        "INSERT INTO branch (branch_code, branch_name, address, phone, branch_status) "
                                + "VALUES ('99', 'สาขาทดสอบ', '-', '-', true)")
                .executeUpdate();
        entityManager.createNativeQuery(
                        "INSERT INTO therapists (branch_id, code, full_name, active) "
                                + "VALUES ((SELECT branch_id FROM branch WHERE branch_code = '99'), 'QQ-T01', 'นักกายภาพบำบัด สาขาอื่น', true)")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        Long otherBranchTherapistId = therapistRepository.findAll().stream()
                .filter(t -> "QQ-T01".equals(t.getCode())).findFirst().orElseThrow().getId();

        assertThatThrownBy(() -> bookingService.reschedule(
                original.id(), new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), otherBranchTherapistId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่ได้สังกัดสาขานี้");
    }

    @Test
    void rejectsRescheduleOfMissingAppointment() {
        assertThatThrownBy(() -> bookingService.reschedule(
                999999L, new AppointmentRescheduleRequest(nineAmTomorrow.plusHours(3), null)))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    private AppointmentCreateRequest request(OffsetDateTime startAt) {
        return new AppointmentCreateRequest("01", patientId, therapistId, serviceId, startAt, "นัดทดสอบ");
    }

    private Long registerPatient() {
        return registrationService.register(new PatientRegistrationRequest(
                "01", "reschedule-test", "reschedule-test@example.com", "password123", "นาย",
                "ทดสอบ เลื่อนนัด", LocalDate.of(1990, 1, 1), "ชาย", "0800000002",
                "NATIONAL_ID", "1100000000997", "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999998", true)).id();
    }
}