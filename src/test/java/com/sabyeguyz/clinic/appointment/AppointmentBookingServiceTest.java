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
class AppointmentBookingServiceTest {
    @Autowired AppointmentBookingService bookingService;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired EntityManager entityManager;

    private Long patientId;
    private Long therapistId;
    private Long serviceId;
    private OffsetDateTime nineAmTomorrow;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        therapistId = therapistRepository.findAll().stream()
                .filter(t -> "RP-T01".equals(t.getCode())).findFirst().orElseThrow().getId();
        serviceId = serviceItemRepository.findAll().stream()
                .filter(s -> "PHYSIO_60".equals(s.getCode())).findFirst().orElseThrow().getId();
        nineAmTomorrow = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void createsScheduledAppointmentUsingServiceDuration() {
        AppointmentResponse response = bookingService.create(request(nineAmTomorrow));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.branchCode()).isEqualTo("RP");
        assertThat(response.endAt()).isEqualTo(response.startAt().plus(Duration.ofMinutes(60)));
        assertThat(response.patient().hn()).isNotBlank();
        assertThat(response.service().durationMinutes()).isEqualTo(60);
    }

    @Test
    void rejectsOverlappingAppointmentForSameTherapist() {
        bookingService.create(request(nineAmTomorrow));

        assertThatThrownBy(() -> bookingService.create(request(nineAmTomorrow.plusMinutes(30))))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("นัดหมายในช่วงเวลานี้แล้ว");
    }

    @Test
    void allowsBackToBackAppointmentForSameTherapist() {
        bookingService.create(request(nineAmTomorrow));

        assertThat(bookingService.create(request(nineAmTomorrow.plusHours(1))).id()).isNotNull();
    }

    @Test
    void rejectsStartTimeInThePast() {
        assertThatThrownBy(() -> bookingService.create(request(nineAmTomorrow.minusDays(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ย้อนหลัง");
    }

    @Test
    void rejectsUnknownPatient() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                "RP", 999999L, therapistId, serviceId, nineAmTomorrow, null);

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่พบผู้ป่วย");
    }

    @Test
    void rejectsInactiveService() {
        entityManager.createNativeQuery("UPDATE services SET active = false WHERE code = 'PHYSIO_60'")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> bookingService.create(request(nineAmTomorrow)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("บริการถูกปิดการใช้งาน");
    }

    @Test
    void rejectsTherapistFromAnotherBranch() {
        entityManager.createNativeQuery(
                        "INSERT INTO branches (code, numeric_code, name, active) VALUES ('QQ', '99', 'สาขาทดสอบ', true)")
                .executeUpdate();
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                "QQ", patientId, therapistId, serviceId, nineAmTomorrow, null);

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่ได้สังกัดสาขานี้");
    }

    private AppointmentCreateRequest request(OffsetDateTime startAt) {
        return new AppointmentCreateRequest("RP", patientId, therapistId, serviceId, startAt, "นัดทดสอบ");
    }

    private Long registerPatient() {
        return registrationService.register(new PatientRegistrationRequest(
                "RP", "booking-test", "booking-test@example.com", "password123", "นาย",
                "ทดสอบ จองนัด", LocalDate.of(1990, 1, 1), "ชาย", "0800000001",
                "NATIONAL_ID", "1100000000998", "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}