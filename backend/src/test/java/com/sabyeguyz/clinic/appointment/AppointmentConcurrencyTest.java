package com.sabyeguyz.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AppointmentConcurrencyTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired AppointmentBookingService bookingService;
    @Autowired AppointmentLifecycleService lifecycleService;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager entityManager;

    private Long patientId;
    private Long therapistId;
    private Long serviceId;
    private OffsetDateTime slot;
    private Instant cleanupFrom;
    private long appointmentBaseline;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        therapistId = therapistId("RP-T01");
        serviceId = serviceItemRepository.findAll().stream()
                .filter(s -> "PHYSIO_60".equals(s.getCode())).findFirst().orElseThrow().getId();
        slot = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(90).withHour(9).withMinute(0).withSecond(0).withNano(0);
        cleanupFrom = slot.toInstant().minusSeconds(86400);
        appointmentBaseline = appointmentRepository.count();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Appointment a where a.startAt >= :from")
                    .setParameter("from", cleanupFrom)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM patients WHERE username LIKE 'concurrency-test-%'")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "DELETE FROM hn_counters WHERE branch_id = (SELECT id FROM branches WHERE code = 'RP')")
                    .executeUpdate();
        });
    }

    @Test
    void allowsOnlyOneBookingWhenTwoRequestsRaceForTheSameSlot() throws Exception {
        AppointmentCreateRequest request = request(slot);

        List<Object> outcomes = runConcurrently(
                () -> bookingService.create(request),
                () -> bookingService.create(request));

        assertThat(countOf(outcomes, AppointmentResponse.class))
                .as("ต้องจองสำเร็จเพียง request เดียว").isEqualTo(1);
        assertThat(countOf(outcomes, AppointmentConflictException.class))
                .as("อีก request ต้องถูกปฏิเสธด้วย 409 conflict").isEqualTo(1);
        assertThat(appointmentRepository.count() - appointmentBaseline)
                .as("ฐานข้อมูลต้องมีนัดหมายเพิ่มขึ้นแถวเดียว").isEqualTo(1);
    }

    @Test
    void allowsOnlyOneRescheduleWhenTwoAppointmentsRaceForTheSameSlot() throws Exception {
        long morning = book(slot);
        long afternoon = book(slot.plusHours(5));
        AppointmentRescheduleRequest target = new AppointmentRescheduleRequest(slot.plusHours(2), null);

        List<Object> outcomes = runConcurrently(
                () -> bookingService.reschedule(morning, target),
                () -> bookingService.reschedule(afternoon, target));

        assertThat(countOf(outcomes, AppointmentResponse.class))
                .as("ต้องเลื่อนนัดสำเร็จเพียง request เดียว").isEqualTo(1);
        assertThat(countOf(outcomes, AppointmentConflictException.class))
                .as("อีก request ต้องถูกปฏิเสธด้วย 409 conflict").isEqualTo(1);
        assertThat(appointmentRepository.findById(morning).orElseThrow().getStartAt())
                .as("นัดที่ถูกปฏิเสธต้องไม่ถูกแก้เวลา")
                .isNotEqualTo(appointmentRepository.findById(afternoon).orElseThrow().getStartAt());
    }

    @Test
    void allowsOnlyOneCheckInWhenTwoRequestsRace() throws Exception {
        long id = book(slot);

        List<Object> outcomes = runConcurrently(
                () -> lifecycleService.checkIn(id),
                () -> lifecycleService.checkIn(id));

        assertThat(countOf(outcomes, AppointmentResponse.class))
                .as("ต้องเช็คอินสำเร็จเพียง request เดียว").isEqualTo(1);
        assertThat(countOf(outcomes, AppointmentConflictException.class))
                .as("request ที่สองต้องเห็นสถานะล่าสุดแล้วถูกปฏิเสธ").isEqualTo(1);
        assertThat(appointmentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.ARRIVED);
    }

    @Test
    void keepsCancellationConsistentWhenCheckInAndCancelRace() throws Exception {
        long id = book(slot);

        List<Object> outcomes = runConcurrently(
                () -> lifecycleService.checkIn(id),
                () -> lifecycleService.cancel(id, new AppointmentCancelRequest("ผู้ป่วยติดธุระ")));

        Appointment finalState = appointmentRepository.findById(id).orElseThrow();
        assertThat(finalState.getStatus())
                .as("ผลลัพธ์ต้องเทียบเท่ากับการรันทีละ request").isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(finalState.getCancelReason()).isNotBlank();
        assertThat(countOf(outcomes, AppointmentResponse.class))
                .as("อย่างน้อยการยกเลิกต้องสำเร็จ").isGreaterThanOrEqualTo(1);
    }

    @Test
    void keepsStateConsistentWhenRescheduleAndCancelRace() throws Exception {
        long id = book(slot);
        OffsetDateTime newSlot = slot.plusHours(3);

        List<Object> outcomes = runConcurrently(
                () -> bookingService.reschedule(id, new AppointmentRescheduleRequest(newSlot, null)),
                () -> lifecycleService.cancel(id, new AppointmentCancelRequest("ผู้ป่วยติดธุระ")));

        boolean rescheduled = outcomes.stream().anyMatch(outcome -> outcome instanceof AppointmentResponse response
                && response.startAt().equals(newSlot.toInstant()));
        boolean cancelled = outcomes.stream().anyMatch(outcome -> outcome instanceof AppointmentResponse response
                && response.status() == AppointmentStatus.CANCELLED);

        assertThat(rescheduled || cancelled).as("อย่างน้อยหนึ่ง request ต้องสำเร็จ").isTrue();

        Appointment finalState = appointmentRepository.findById(id).orElseThrow();
        if (rescheduled) {
            assertThat(finalState.getStartAt())
                    .as("เลื่อนนัดตอบสำเร็จแล้ว เวลาต้องถูกบันทึกจริง").isEqualTo(newSlot.toInstant());
        }
        if (cancelled) {
            assertThat(finalState.getStatus())
                    .as("ยกเลิกตอบสำเร็จแล้ว สถานะต้องถูกบันทึกจริง").isEqualTo(AppointmentStatus.CANCELLED);
        }
    }

    private List<Object> runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return (Object) task.call();
                    } catch (Exception failure) {
                        return failure;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("ทั้งสอง thread ต้องพร้อมก่อนเริ่ม").isTrue();
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long countOf(List<Object> outcomes, Class<?> type) {
        return outcomes.stream().filter(type::isInstance).count();
    }

    private long book(OffsetDateTime startAt) {
        return bookingService.create(request(startAt)).id();
    }

    private AppointmentCreateRequest request(OffsetDateTime startAt) {
        return new AppointmentCreateRequest("RP", patientId, therapistId, serviceId, startAt, "นัดทดสอบ concurrency");
    }

    private Long therapistId(String code) {
        return therapistRepository.findAll().stream()
                .filter(t -> code.equals(t.getCode())).findFirst().orElseThrow().getId();
    }

    private Long registerPatient() {
        int seq = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "RP", "concurrency-test-" + seq, "concurrency-test-" + seq + "@example.com", "password123", "นาย",
                "ทดสอบ พร้อมกัน", LocalDate.of(1990, 1, 1), "ชาย", "0800000000",
                "NATIONAL_ID", "99000000000" + String.format("%02d", seq), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
