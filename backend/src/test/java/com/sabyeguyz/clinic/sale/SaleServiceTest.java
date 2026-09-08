package com.sabyeguyz.clinic.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sabyeguyz.clinic.auth.AppUser;
import com.sabyeguyz.clinic.auth.AppUserRepository;
import com.sabyeguyz.clinic.auth.Role;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.course.CourseLedgerService;
import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.course.CourseUsageRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.staff.StaffRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SaleServiceTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired SaleService service;
    @Autowired SaleItemRepository itemRepository;
    @Autowired SalePaymentRepository paymentRepository;
    @Autowired CourseLedgerService courseLedgerService;
    @Autowired CourseRepository courseRepository;
    @Autowired ServiceItemRepository serviceRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired PatientRegistrationService registrationService;
    @PersistenceContext EntityManager entityManager;

    private Long patientId;
    private Long courseId;
    private Long assessmentServiceId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        // 10 ครั้ง + แถม 3 = 13 ครั้ง ราคา 15,000 ตรงกับตัวอย่างใน brief
        courseId = courseRepository.save(new Course(
                "SALE" + UNIQUE.incrementAndGet(), "คอร์สกายภาพ 10 ครั้ง",
                new BigDecimal("15000.00"), 10, 3, 90)).getId();
        assessmentServiceId = serviceRepository.findByCode("ASSESSMENT").orElseThrow().getId();
    }

    @Test
    void pricesEveryLineFromTheMasterAndIssuesAReceipt() {
        SaleResponse sale = service.create(request(
                List.of(assessmentLine(1)),
                List.of(payment("CASH", "1200.00"))));

        assertThat(sale.total()).isEqualByComparingTo("1200.00");
        assertThat(sale.paid()).isEqualByComparingTo("1200.00");
        assertThat(sale.status()).isEqualTo(SaleStatus.PAID);
        assertThat(sale.items()).singleElement()
                .satisfies(item -> assertThat(item.unitPrice()).isEqualByComparingTo("1200.00"));
    }

    @Test
    void receiptNumberCarriesYearBranchAndMonth() {
        SaleResponse sale = service.create(request(
                List.of(assessmentLine(1)),
                List.of(payment("CASH", "1200.00"))));

        // RC + ปี 2 หลัก + สาขา 2 หลัก + เดือน 2 หลัก + running 4 หลัก
        assertThat(sale.receiptNo()).matches("RC\\d{10}");
    }

    @Test
    void multipliesUnitPriceByQuantity() {
        SaleResponse sale = service.create(request(
                List.of(assessmentLine(3)),
                List.of(payment("CASH", "3600.00"))));

        assertThat(sale.total()).isEqualByComparingTo("3600.00");
        assertThat(sale.items().get(0).lineTotal()).isEqualByComparingTo("3600.00");
    }

    @Test
    void acceptsPaymentSplitAcrossSeveralMethods() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "5000.00"), payment("QR", "10000.00"))));

        assertThat(sale.paid()).isEqualByComparingTo("15000.00");
        assertThat(sale.payments()).extracting(SaleResponse.SalePaymentResponse::paymentMethodCode)
                .containsExactly("CASH", "QR");
    }

    @Test
    void rejectsPaymentsThatDoNotAddUpToTheTotal() {
        assertThatThrownBy(() -> service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "14000.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่ตรงกับยอดที่ต้องชำระ");
    }

    @Test
    void rejectsOverpayment() {
        assertThatThrownBy(() -> service.create(request(
                List.of(assessmentLine(1)),
                List.of(payment("CASH", "2000.00")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sellingACourseCreditsItsSessionsImmediately() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "15000.00"))));

        Long purchaseId = sale.items().get(0).coursePurchaseId();
        assertThat(purchaseId).isNotNull();
        assertThat(courseLedgerService.ledger(purchaseId).purchase().remaining()).isEqualTo(13);
    }

    @Test
    void sellingACourseAndUsingItOnTheSameDayWorks() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "15000.00"))));
        Long purchaseId = sale.items().get(0).coursePurchaseId();

        courseLedgerService.use(new CourseUsageRequest(purchaseId, null, "ใช้วันเดียวกับที่ซื้อ"));

        assertThat(courseLedgerService.ledger(purchaseId).purchase().used()).isEqualTo(1);
    }

    @Test
    void sellsAServiceAndACourseOnTheSameReceipt() {
        SaleResponse sale = service.create(request(
                List.of(assessmentLine(1), courseLine()),
                List.of(payment("CASH", "16200.00"))));

        assertThat(sale.total()).isEqualByComparingTo("16200.00");
        assertThat(sale.items()).hasSize(2);
        assertThat(sale.items().get(0).coursePurchaseId()).isNull();
        assertThat(sale.items().get(1).coursePurchaseId()).isNotNull();
    }

    @Test
    void storesTheSalespersonSeparatelyFromTheTreatingStaff() {
        Long salespersonId = registerSalesperson();

        SaleResponse sale = service.create(new SaleRequest(patientId, "RP", salespersonId, null,
                List.of(assessmentLine(1)), List.of(payment("CASH", "1200.00"))));

        assertThat(sale.salesperson()).isNotNull();
        assertThat(sale.salesperson().id()).isEqualTo(salespersonId);
    }

    @Test
    void rejectsSellingMoreThanOneCourseOnASingleLine() {
        assertThatThrownBy(() -> service.create(request(
                List.of(new SaleRequest.Item(SaleItemType.COURSE, courseId, 2)),
                List.of(payment("CASH", "30000.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ทีละ 1 ใบ");
    }

    @Test
    void rejectsAnInactiveCourse() {
        Course course = courseRepository.findById(courseId).orElseThrow();
        course.deactivate();
        courseRepository.saveAndFlush(course);

        assertThatThrownBy(() -> service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "15000.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ปิดการใช้งาน");
    }

    @Test
    void rejectsAServiceThatHasNoPriceYet() {
        Long unpricedId = insertUnpricedService();

        assertThatThrownBy(() -> service.create(request(
                List.of(new SaleRequest.Item(SaleItemType.SINGLE_VISIT, unpricedId, 1)),
                List.of(payment("CASH", "500.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ยังไม่ได้ตั้งราคา");
    }

    @Test
    void rejectsAnUnknownPaymentMethod() {
        assertThatThrownBy(() -> service.create(request(
                List.of(assessmentLine(1)),
                List.of(payment("BITCOIN", "1200.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่พบช่องทางชำระเงิน");
    }

    @Test
    void voidingReversesTheRevenueAndCancelsTheCourseItSold() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "15000.00"))));
        Long purchaseId = sale.items().get(0).coursePurchaseId();

        SaleResponse voided = service.voidSale(sale.id(), new SaleVoidRequest("ลูกค้าขอยกเลิก"));

        assertThat(voided.status()).isEqualTo(SaleStatus.VOIDED);
        assertThat(voided.paid()).as("รายได้ต้องถูกกลับรายการจนเหลือศูนย์").isEqualByComparingTo("0.00");
        assertThat(voided.voidReason()).isEqualTo("ลูกค้าขอยกเลิก");
        assertThat(voided.voidedAt()).isNotNull();
        assertThat(courseLedgerService.ledger(purchaseId).purchase().status())
                .isEqualTo(CoursePurchaseStatus.CANCELLED);
    }

    @Test
    void voidingKeepsSessionsThePatientAlreadyUsed() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "15000.00"))));
        Long purchaseId = sale.items().get(0).coursePurchaseId();
        courseLedgerService.use(new CourseUsageRequest(purchaseId, null, null));

        service.voidSale(sale.id(), new SaleVoidRequest("ยกเลิกหลังใช้ไปแล้ว 1 ครั้ง"));

        var purchase = courseLedgerService.ledger(purchaseId).purchase();
        assertThat(purchase.used()).as("ครั้งที่ใช้ไปแล้วเกิดขึ้นจริง ต้องไม่หายไป").isEqualTo(1);
        assertThat(purchase.remaining()).isZero();
    }

    @Test
    void voidingDeletesNothing() {
        SaleResponse sale = service.create(request(
                List.of(assessmentLine(1), courseLine()),
                List.of(payment("CASH", "10000.00"), payment("QR", "6200.00"))));

        service.voidSale(sale.id(), new SaleVoidRequest("บันทึกผิดคน"));

        assertThat(itemRepository.findBySaleIdOrderByIdAsc(sale.id())).hasSize(2);
        List<SalePayment> payments = paymentRepository.findBySaleIdOrderByIdAsc(sale.id());
        assertThat(payments).as("2 รายการรับเงินเดิม + 2 รายการคืนเงิน").hasSize(4);
        assertThat(payments).extracting(SalePayment::getType).containsExactly(
                SalePaymentType.PAYMENT, SalePaymentType.PAYMENT,
                SalePaymentType.REFUND, SalePaymentType.REFUND);
        assertThat(payments.get(2).getReversesPayment().getId()).isEqualTo(payments.get(0).getId());
    }

    @Test
    void rejectsVoidingTheSameReceiptTwice() {
        SaleResponse sale = service.create(request(
                List.of(assessmentLine(1)),
                List.of(payment("CASH", "1200.00"))));
        service.voidSale(sale.id(), new SaleVoidRequest("ครั้งแรก"));

        assertThatThrownBy(() -> service.voidSale(sale.id(), new SaleVoidRequest("ครั้งที่สอง")))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("ถูกยกเลิกไปแล้ว");
    }

    @Test
    void voidingSkipsACourseThatWasAlreadyCancelledOnItsOwn() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()),
                List.of(payment("CASH", "15000.00"))));
        Long purchaseId = sale.items().get(0).coursePurchaseId();
        courseLedgerService.cancel(purchaseId,
                new com.sabyeguyz.clinic.course.CourseReasonRequest("ยกเลิกคอร์สก่อน"));

        SaleResponse voided = service.voidSale(sale.id(), new SaleVoidRequest("แล้วค่อยยกเลิกใบเสร็จ"));

        assertThat(voided.status()).isEqualTo(SaleStatus.VOIDED);
        assertThat(courseLedgerService.ledger(purchaseId).movements())
                .as("ต้องไม่มีรายการยกเลิกคอร์สซ้ำสองรอบ").hasSize(2);
    }

    @Test
    void listsReceiptsOfThePatientIncludingVoidedOnes() {
        SaleResponse first = service.create(request(
                List.of(assessmentLine(1)), List.of(payment("CASH", "1200.00"))));
        service.create(request(List.of(assessmentLine(1)), List.of(payment("CASH", "1200.00"))));
        service.voidSale(first.id(), new SaleVoidRequest("ยกเลิกใบแรก"));

        SaleSearchResponse page = service.search(patientId, null, 0, 20);

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.items()).extracting(SaleSummaryResponse::status)
                .contains(SaleStatus.VOIDED, SaleStatus.PAID);
    }

    @Test
    void listShowsMoneyActuallyKeptSoAVoidedReceiptReadsZero() {
        SaleResponse sale = service.create(request(
                List.of(courseLine()), List.of(payment("CASH", "15000.00"))));
        service.voidSale(sale.id(), new SaleVoidRequest("ยกเลิก"));

        SaleSummaryResponse summary = service.search(patientId, null, 0, 20).items().get(0);

        assertThat(summary.total()).as("ยอดที่ต้องชำระเดิมยังอยู่").isEqualByComparingTo("15000.00");
        assertThat(summary.paid()).as("แต่เงินที่เก็บไว้จริงเป็นศูนย์").isEqualByComparingTo("0.00");
    }

    @Test
    void listsEveryPatientWhenNoFilterIsGiven() {
        Long other = registerPatient();
        service.create(request(List.of(assessmentLine(1)), List.of(payment("CASH", "1200.00"))));
        service.create(new SaleRequest(other, "RP", null, null,
                List.of(assessmentLine(1)), List.of(payment("CASH", "1200.00"))));

        assertThat(service.search(null, null, 0, 20).totalItems()).isGreaterThanOrEqualTo(2);
        assertThat(service.search(other, null, 0, 20).totalItems()).isEqualTo(1);
    }

    @Test
    void rejectsAnOversizedPage() {
        assertThatThrownBy(() -> service.search(null, null, 0, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void rejectsAnUnknownPatientFilter() {
        assertThatThrownBy(() -> service.search(999999L, null, 0, 20))
                .isInstanceOf(com.sabyeguyz.clinic.patient.PatientNotFoundException.class);
    }

    private SaleRequest request(List<SaleRequest.Item> items, List<SaleRequest.Payment> payments) {
        return new SaleRequest(patientId, "RP", null, null, items, payments);
    }

    private SaleRequest.Item assessmentLine(int quantity) {
        return new SaleRequest.Item(SaleItemType.ASSESSMENT, assessmentServiceId, quantity);
    }

    private SaleRequest.Item courseLine() {
        return new SaleRequest.Item(SaleItemType.COURSE, courseId, 1);
    }

    private static SaleRequest.Payment payment(String methodCode, String amount) {
        return new SaleRequest.Payment(methodCode, new BigDecimal(amount));
    }

    /** ServiceItem ไม่มี constructor สาธารณะ จึงต้องหยอดผ่าน SQL ตรงเพื่อทดสอบกรณีไม่ได้ตั้งราคา */
    private Long insertUnpricedService() {
        String code = "NOPRICE" + UNIQUE.incrementAndGet();
        entityManager.createNativeQuery(
                        "insert into services(code, name, default_duration_minutes, price, active) "
                                + "values (?1, ?2, 30, 0, true)")
                .setParameter(1, code)
                .setParameter(2, "บริการที่ยังไม่ตั้งราคา")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        ServiceItem inserted = serviceRepository.findByCode(code).orElseThrow();
        return inserted.getId();
    }

    private Long registerSalesperson() {
        int unique = UNIQUE.incrementAndGet();
        AppUser user = userRepository.save(
                new AppUser("sale-person-" + unique, "hash", Role.STAFF));
        return staffRepository.save(
                new Staff(user, "SP" + unique, "พนักงานขาย ทดสอบ", "0800000013")).getId();
    }

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "RP", "sale-test-" + unique, "sale-test-" + unique + "@example.com", "password123", "นาย",
                "ทดสอบ การขาย", LocalDate.of(1990, 1, 1), "ชาย", "0800000014",
                "NATIONAL_ID", "1100000006" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
