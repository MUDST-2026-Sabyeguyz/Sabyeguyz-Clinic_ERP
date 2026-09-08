package com.sabyeguyz.clinic.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/**
 * ยอดคงเหลือของคอร์สห้ามติดลบแม้มีสอง request ตัดสิทธิ์ครั้งสุดท้ายพร้อมกัน
 * เทสนี้ไม่ใช้ @Transactional เพราะต้องให้แต่ละ thread มี transaction ของตัวเองจริงๆ
 */
@SpringBootTest
class CourseLedgerConcurrencyTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired CourseLedgerService service;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseMovementRepository movementRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager entityManager;

    private Long purchaseId;

    @BeforeEach
    void setUp() {
        Long patientId = registerPatient();
        // 1 ครั้ง ไม่มี Bonus เพื่อให้เหลือสิทธิ์ครั้งเดียวให้แย่งกัน
        Long courseId = courseRepository.save(new Course(
                "CONC" + UNIQUE.incrementAndGet(), "คอร์สครั้งเดียว", new BigDecimal("1000.00"), 1, 0, 30)).getId();
        purchaseId = service.purchase(new CoursePurchaseRequest(patientId, courseId, "RP")).id();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            String ownedMovements = "SELECT id FROM course_purchases WHERE patient_id IN "
                    + "(SELECT id FROM patients WHERE username LIKE 'course-concurrency-%')";
            // course_movements อ้างถึงตัวเอง ต้องตัดลิงก์ก่อน ไม่งั้น DELETE ชน FK
            entityManager.createNativeQuery(
                            "UPDATE course_movements SET reverses_movement_id = NULL "
                                    + "WHERE course_purchase_id IN (" + ownedMovements + ")")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "DELETE FROM course_movements WHERE course_purchase_id IN (" + ownedMovements + ")")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "DELETE FROM course_purchases WHERE patient_id IN "
                                    + "(SELECT id FROM patients WHERE username LIKE 'course-concurrency-%')")
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM patients WHERE username LIKE 'course-concurrency-%'")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "DELETE FROM hn_counters WHERE branch_id = (SELECT id FROM branches WHERE code = 'RP')")
                    .executeUpdate();
        });
    }

    @Test
    void allowsOnlyOneUsageWhenTwoRequestsRaceForTheLastSession() throws Exception {
        CourseUsageRequest request = new CourseUsageRequest(purchaseId, null, "แย่งครั้งสุดท้าย");

        List<Object> outcomes = runConcurrently(
                () -> service.use(request),
                () -> service.use(request));

        assertThat(countOf(outcomes, CoursePurchaseResponse.class))
                .as("ต้องตัดสิทธิ์สำเร็จเพียง request เดียว").isEqualTo(1);
        assertThat(countOf(outcomes, CourseBalanceException.class))
                .as("อีก request ต้องถูกปฏิเสธ").isEqualTo(1);
        assertThat(movementRepository.balanceOf(purchaseId))
                .as("ยอดคงเหลือห้ามติดลบ").isZero();
    }

    @Test
    void doesNotDoubleReverseWhenTwoRequestsRaceOnTheSameUsage() throws Exception {
        service.use(new CourseUsageRequest(purchaseId, null, null));
        Long usageId = service.ledger(purchaseId).movements().stream()
                .filter(movement -> movement.type() == CourseMovementType.USAGE)
                .findFirst().orElseThrow().id();
        CourseReasonRequest reason = new CourseReasonRequest("บันทึกผิด");

        List<Object> outcomes = runConcurrently(
                () -> service.reverseUsage(usageId, reason),
                () -> service.reverseUsage(usageId, reason));

        assertThat(countOf(outcomes, CoursePurchaseResponse.class))
                .as("กลับรายการได้ครั้งเดียว").isEqualTo(1);
        assertThat(movementRepository.balanceOf(purchaseId))
                .as("สิทธิ์ต้องคืนกลับมาแค่ครั้งเดียว").isEqualTo(1);
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

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "RP", "course-concurrency-" + unique, "course-concurrency-" + unique + "@example.com",
                "password123", "นาย", "ทดสอบ คอร์สพร้อมกัน", LocalDate.of(1990, 1, 1), "ชาย", "0800000011",
                "NATIONAL_ID", "1100000008" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
