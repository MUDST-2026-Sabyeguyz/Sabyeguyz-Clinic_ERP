package com.sabyeguyz.clinic.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CourseLedgerServiceTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired CourseLedgerService service;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseMovementRepository movementRepository;
    @Autowired PatientRegistrationService registrationService;
    @PersistenceContext EntityManager entityManager;

    private Long patientId;
    private Long courseId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        // 10 ครั้ง + แถม 3 = 13 ครั้ง อายุ 90 วัน ตรงกับตัวอย่าง Course Ledger ใน brief
        courseId = courseRepository.save(
                new Course(uniqueCode("PKG"), "คอร์สกายภาพ 10 ครั้ง", new BigDecimal("15000.00"), 10, 3, 90)).getId();
    }

    @Test
    void purchaseCreditsSessionsPlusBonus() {
        CoursePurchaseResponse response = purchase();

        assertThat(response.purchased()).isEqualTo(13);
        assertThat(response.used()).isZero();
        assertThat(response.remaining()).isEqualTo(13);
        assertThat(response.usable()).isTrue();
        assertThat(response.status()).isEqualTo(CoursePurchaseStatus.ACTIVE);
    }

    @Test
    void purchaseSnapshotsCourseMasterSoLaterEditsDoNotChangeSoldPackages() {
        CoursePurchaseResponse bought = purchase();

        Course master = courseRepository.findById(courseId).orElseThrow();
        master.update(master.getCode(), "ชื่อใหม่", new BigDecimal("99999.00"), 99, 99, 1);
        courseRepository.saveAndFlush(master);

        CoursePurchaseResponse reloaded = service.ledger(bought.id()).purchase();
        assertThat(reloaded.price()).isEqualByComparingTo("15000.00");
        assertThat(reloaded.purchased()).isEqualTo(13);
        assertThat(reloaded.course().name()).isEqualTo("คอร์สกายภาพ 10 ครั้ง");
    }

    @Test
    void expiryIsCalculatedFromValidityDays() {
        CoursePurchaseResponse response = purchase();

        assertThat(response.expiresAt()).isEqualTo(response.purchasedAt().plus(java.time.Duration.ofDays(90)));
        assertThat(response.expired()).isFalse();
    }

    @Test
    void buyingAndUsingOnTheSameDayWorks() {
        CoursePurchaseResponse bought = purchase();

        CoursePurchaseResponse afterUse = service.use(new CourseUsageRequest(bought.id(), null, "ใช้วันเดียวกับที่ซื้อ"));

        assertThat(afterUse.used()).isEqualTo(1);
        assertThat(afterUse.remaining()).isEqualTo(12);
    }

    @Test
    void balanceIsTheSumOfMovementsNotAStoredCounter() {
        CoursePurchaseResponse bought = purchase();
        service.use(new CourseUsageRequest(bought.id(), null, null));
        service.use(new CourseUsageRequest(bought.id(), null, null));

        CourseLedgerResponse ledger = service.ledger(bought.id());

        assertThat(ledger.movements()).extracting(CourseMovementResponse::quantity).containsExactly(13, -1, -1);
        assertThat(ledger.movements()).extracting(CourseMovementResponse::balanceAfter).containsExactly(13, 12, 11);
        assertThat(ledger.purchase().remaining()).isEqualTo(11);
    }

    @Test
    void rejectsUsageWhenNoSessionsLeft() {
        CoursePurchaseResponse bought = purchase();
        for (int i = 0; i < 13; i++) {
            service.use(new CourseUsageRequest(bought.id(), null, null));
        }

        assertThatThrownBy(() -> service.use(new CourseUsageRequest(bought.id(), null, null)))
                .isInstanceOf(CourseBalanceException.class)
                .hasMessageContaining("ครบแล้ว");
        assertThat(service.ledger(bought.id()).purchase().remaining()).isZero();
    }

    @Test
    void rejectsUsageOfExpiredCourse() {
        Long expiredCourseId = courseRepository.save(
                new Course(uniqueCode("EXP"), "คอร์สอายุสั้น", new BigDecimal("1000.00"), 5, 0, 1)).getId();
        CoursePurchaseResponse bought = service.purchase(new CoursePurchaseRequest(patientId, expiredCourseId, "01"));

        // ย้อนวันหมดอายุให้ผ่านไปแล้ว โดยไม่แตะยอดคงเหลือ
        expirePurchase(bought.id());

        assertThatThrownBy(() -> service.use(new CourseUsageRequest(bought.id(), null, null)))
                .isInstanceOf(CourseBalanceException.class)
                .hasMessageContaining("หมดอายุ");
    }

    @Test
    void reversingUsageGivesTheSessionBackAndKeepsTheOriginalRow() {
        CoursePurchaseResponse bought = purchase();
        service.use(new CourseUsageRequest(bought.id(), null, null));
        Long usageId = service.ledger(bought.id()).movements().stream()
                .filter(m -> m.type() == CourseMovementType.USAGE).findFirst().orElseThrow().id();

        CoursePurchaseResponse afterReversal =
                service.reverseUsage(usageId, new CourseReasonRequest("บันทึกผิดคน"));

        assertThat(afterReversal.remaining()).isEqualTo(13);
        assertThat(afterReversal.used()).isZero();
        CourseLedgerResponse ledger = service.ledger(bought.id());
        assertThat(ledger.movements()).hasSize(3);
        assertThat(ledger.movements().get(1).type()).isEqualTo(CourseMovementType.USAGE);
        assertThat(ledger.movements().get(2).reversesMovementId()).isEqualTo(usageId);
    }

    @Test
    void rejectsReversingTheSameMovementTwice() {
        CoursePurchaseResponse bought = purchase();
        service.use(new CourseUsageRequest(bought.id(), null, null));
        Long usageId = service.ledger(bought.id()).movements().stream()
                .filter(m -> m.type() == CourseMovementType.USAGE).findFirst().orElseThrow().id();
        service.reverseUsage(usageId, new CourseReasonRequest("บันทึกผิดคน"));

        assertThatThrownBy(() -> service.reverseUsage(usageId, new CourseReasonRequest("กดซ้ำ")))
                .isInstanceOf(CourseBalanceException.class)
                .hasMessageContaining("ถูกกลับรายการไปแล้ว");
    }

    @Test
    void rejectsReversingAPurchaseMovement() {
        CoursePurchaseResponse bought = purchase();
        Long purchaseMovementId = service.ledger(bought.id()).movements().get(0).id();

        assertThatThrownBy(() -> service.reverseUsage(purchaseMovementId, new CourseReasonRequest("ลองดู")))
                .isInstanceOf(CourseBalanceException.class)
                .hasMessageContaining("เฉพาะรายการใช้สิทธิ์");
    }

    @Test
    void cancellingWritesOffTheRemainingBalanceButKeepsUsedSessions() {
        CoursePurchaseResponse bought = purchase();
        service.use(new CourseUsageRequest(bought.id(), null, null));
        service.use(new CourseUsageRequest(bought.id(), null, null));

        CoursePurchaseResponse cancelled = service.cancel(bought.id(), new CourseReasonRequest("ผู้ป่วยขอยกเลิก"));

        assertThat(cancelled.status()).isEqualTo(CoursePurchaseStatus.CANCELLED);
        assertThat(cancelled.remaining()).isZero();
        assertThat(cancelled.used()).isEqualTo(2);
        assertThat(cancelled.usable()).isFalse();
    }

    @Test
    void rejectsUsageAfterCancellation() {
        CoursePurchaseResponse bought = purchase();
        service.cancel(bought.id(), new CourseReasonRequest("ผู้ป่วยขอยกเลิก"));

        assertThatThrownBy(() -> service.use(new CourseUsageRequest(bought.id(), null, null)))
                .isInstanceOf(CourseBalanceException.class)
                .hasMessageContaining("ถูกยกเลิกแล้ว");
    }

    @Test
    void rejectsCancellingTwice() {
        CoursePurchaseResponse bought = purchase();
        service.cancel(bought.id(), new CourseReasonRequest("ครั้งแรก"));

        assertThatThrownBy(() -> service.cancel(bought.id(), new CourseReasonRequest("ครั้งที่สอง")))
                .isInstanceOf(CourseBalanceException.class);
    }

    @Test
    void nothingIsEverDeletedFromTheLedger() {
        CoursePurchaseResponse bought = purchase();
        service.use(new CourseUsageRequest(bought.id(), null, null));
        Long usageId = service.ledger(bought.id()).movements().stream()
                .filter(m -> m.type() == CourseMovementType.USAGE).findFirst().orElseThrow().id();
        service.reverseUsage(usageId, new CourseReasonRequest("แก้ไข"));
        service.cancel(bought.id(), new CourseReasonRequest("ยกเลิกคอร์ส"));

        assertThat(movementRepository.findByCoursePurchaseIdOrderByIdAsc(bought.id())).hasSize(4);
    }

    @Test
    void listsCoursesOfThePatient() {
        purchase();
        purchase();

        assertThat(service.listByPatient(patientId)).hasSize(2);
    }

    private CoursePurchaseResponse purchase() {
        return service.purchase(new CoursePurchaseRequest(patientId, courseId, "01"));
    }

    private void expirePurchase(long purchaseId) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update course_purchases set purchased_at = ?1, expires_at = ?2 where id = ?3")
                .setParameter(1, java.time.Instant.now().minus(java.time.Duration.ofDays(10)))
                .setParameter(2, java.time.Instant.now().minus(java.time.Duration.ofDays(9)))
                .setParameter(3, purchaseId)
                .executeUpdate();
        entityManager.clear();
    }

    private static String uniqueCode(String prefix) {
        return prefix + UNIQUE.incrementAndGet();
    }

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "01", "course-test-" + unique, "course-test-" + unique + "@example.com", "password123", "นาย",
                "ทดสอบ คอร์ส", LocalDate.of(1990, 1, 1), "ชาย", "0800000010",
                "NATIONAL_ID", "1100000009" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
