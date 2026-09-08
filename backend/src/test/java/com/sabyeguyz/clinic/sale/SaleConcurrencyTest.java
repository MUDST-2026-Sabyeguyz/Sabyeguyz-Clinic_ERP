package com.sabyeguyz.clinic.sale;

import static org.assertj.core.api.Assertions.assertThat;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.course.CourseLedgerService;
import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
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
 * เลขที่ใบเสร็จห้ามซ้ำ และการยกเลิกใบเดียวกันพร้อมกันห้ามคืนเงินสองรอบ
 * เทสนี้ไม่ใช้ @Transactional เพราะแต่ละ thread ต้องมี transaction ของตัวเองจริงๆ
 */
@SpringBootTest
class SaleConcurrencyTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();
    private static final String USERNAME_PREFIX = "sale-concurrency-";

    @Autowired SaleService service;
    @Autowired SalePaymentRepository paymentRepository;
    @Autowired CourseLedgerService courseLedgerService;
    @Autowired CourseRepository courseRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager entityManager;

    private Long patientId;
    private Long courseId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        courseId = courseRepository.save(new Course(
                "CSALE" + UNIQUE.incrementAndGet(), "คอร์สทดสอบพร้อมกัน",
                new BigDecimal("5000.00"), 4, 0, 30)).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            String ownedPatients = "SELECT id FROM patients WHERE username LIKE '" + USERNAME_PREFIX + "%'";
            String ownedSales = "SELECT id FROM sales WHERE patient_id IN (" + ownedPatients + ")";
            String ownedPurchases = "SELECT id FROM course_purchases WHERE patient_id IN (" + ownedPatients + ")";
            // sale_payments และ course_movements อ้างถึงตัวเอง ต้องตัดลิงก์ก่อน ไม่งั้น DELETE ชน FK
            execute("UPDATE sale_payments SET reverses_payment_id = NULL WHERE sale_id IN (" + ownedSales + ")");
            execute("DELETE FROM sale_payments WHERE sale_id IN (" + ownedSales + ")");
            execute("DELETE FROM sale_items WHERE sale_id IN (" + ownedSales + ")");
            execute("UPDATE course_movements SET reverses_movement_id = NULL "
                    + "WHERE course_purchase_id IN (" + ownedPurchases + ")");
            execute("DELETE FROM course_movements WHERE course_purchase_id IN (" + ownedPurchases + ")");
            execute("DELETE FROM sales WHERE patient_id IN (" + ownedPatients + ")");
            execute("DELETE FROM course_purchases WHERE patient_id IN (" + ownedPatients + ")");
            execute("DELETE FROM patients WHERE username LIKE '" + USERNAME_PREFIX + "%'");
            execute("DELETE FROM hn_counters WHERE branch_id = (SELECT id FROM branches WHERE code = 'RP')");
            execute("DELETE FROM receipt_counters WHERE branch_id = (SELECT id FROM branches WHERE code = 'RP')");
        });
    }

    @Test
    void neverIssuesTheSameReceiptNumberTwice() throws Exception {
        List<Object> outcomes = runConcurrently(
                () -> service.create(courseSale()),
                () -> service.create(courseSale()));

        List<String> receiptNumbers = outcomes.stream()
                .filter(SaleResponse.class::isInstance)
                .map(outcome -> ((SaleResponse) outcome).receiptNo())
                .toList();
        assertThat(receiptNumbers).as("ทั้งสองใบต้องออกสำเร็จ แต่ได้ %s", outcomes).hasSize(2);
        assertThat(receiptNumbers).as("เลขที่ใบเสร็จห้ามซ้ำ").doesNotHaveDuplicates();
    }

    @Test
    void doesNotRefundTwiceWhenTwoRequestsVoidTheSameReceipt() throws Exception {
        SaleResponse sale = service.create(courseSale());
        Long purchaseId = sale.items().get(0).coursePurchaseId();

        List<Object> outcomes = runConcurrently(
                () -> service.voidSale(sale.id(), new SaleVoidRequest("กดพร้อมกัน")),
                () -> service.voidSale(sale.id(), new SaleVoidRequest("กดพร้อมกัน")));

        assertThat(outcomes.stream().filter(SaleResponse.class::isInstance).count())
                .as("ยกเลิกสำเร็จได้ครั้งเดียว").isEqualTo(1);
        assertThat(paymentRepository.paidAmountOf(sale.id()))
                .as("รายได้ต้องเหลือศูนย์ ไม่ใช่ติดลบจากการคืนเงินสองรอบ").isEqualByComparingTo("0.00");
        assertThat(paymentRepository.findBySaleIdOrderByIdAsc(sale.id()))
                .as("1 รายการรับเงิน + 1 รายการคืนเงิน").hasSize(2);
        assertThat(courseLedgerService.ledger(purchaseId).purchase().status())
                .isEqualTo(CoursePurchaseStatus.CANCELLED);
    }

    private SaleRequest courseSale() {
        return new SaleRequest(patientId, "RP", null, null,
                List.of(new SaleRequest.Item(SaleItemType.COURSE, courseId, 1)),
                List.of(new SaleRequest.Payment("CASH", new BigDecimal("5000.00"))));
    }

    private void execute(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
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

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "RP", USERNAME_PREFIX + unique, USERNAME_PREFIX + unique + "@example.com", "password123", "นาย",
                "ทดสอบ ใบเสร็จพร้อมกัน", LocalDate.of(1990, 1, 1), "ชาย", "0800000016",
                "NATIONAL_ID", "1100000004" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
