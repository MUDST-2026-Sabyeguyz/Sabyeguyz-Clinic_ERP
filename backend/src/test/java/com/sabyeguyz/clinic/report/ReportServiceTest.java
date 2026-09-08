package com.sabyeguyz.clinic.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sabyeguyz.clinic.appointment.Appointment;
import com.sabyeguyz.clinic.appointment.AppointmentRepository;
import com.sabyeguyz.clinic.appointment.AppointmentStatus;
import com.sabyeguyz.clinic.auth.AppUser;
import com.sabyeguyz.clinic.auth.AppUserRepository;
import com.sabyeguyz.clinic.auth.Role;
import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.course.CourseLedgerService;
import com.sabyeguyz.clinic.course.CourseReasonRequest;
import com.sabyeguyz.clinic.course.CourseUsageRequest;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.sale.SaleItemType;
import com.sabyeguyz.clinic.sale.SaleRequest;
import com.sabyeguyz.clinic.sale.SaleResponse;
import com.sabyeguyz.clinic.sale.SaleService;
import com.sabyeguyz.clinic.sale.SaleVoidRequest;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.staff.StaffRepository;
import com.sabyeguyz.clinic.therapist.Therapist;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReportServiceTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();
    /** ช่วงเวลาในอนาคตไกลๆ เพื่อไม่ให้ข้อมูลของเทสอื่นหลุดเข้ามาปนในรายงาน */
    private static final LocalDate DAY = LocalDate.of(2031, 5, 12);
    private static final ReportPeriod PERIOD = new ReportPeriod(DAY, DAY);

    @Autowired ReportService service;
    @Autowired SaleService saleService;
    @Autowired CourseLedgerService courseLedgerService;
    @Autowired PatientRegistrationService registrationService;
    @Autowired PatientRepository patientRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired ServiceItemRepository serviceRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired TherapistRepository therapistRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @PersistenceContext EntityManager entityManager;

    private Long patientId;
    private Long courseId;
    private Long assessmentId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient("ชาย");
        courseId = courseRepository.save(new Course("RPT" + UNIQUE.incrementAndGet(), "คอร์สรายงาน",
                new BigDecimal("15000.00"), 10, 3, 90)).getId();
        assessmentId = serviceRepository.findByCode("ASSESSMENT").orElseThrow().getId();
    }

    // ---------- รายได้ ----------

    @Test
    void totalRevenueEqualsTheSumOfTheCategoryBreakdown() {
        sellOn(DAY, patientId, null, List.of(courseLine(), assessmentLine(2)), "17400.00");

        RevenueReportResponse report = service.revenue(PERIOD, "RP");

        BigDecimal categorySum = report.byCategory().stream()
                .map(RevenueReportResponse.CategoryRevenue::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(categorySum)
                .as("ยอดแยกประเภทต้องบวกกันได้เท่ารายได้รวม ไม่งั้นรายงานสองหน้าจะไม่ตรงกัน")
                .isEqualByComparingTo(report.totalRevenue());
        assertThat(report.totalRevenue()).isEqualByComparingTo("17400.00");
    }

    @Test
    void splitsRevenueBetweenCourseAndSingleVisit() {
        sellOn(DAY, patientId, null, List.of(courseLine()), "15000.00");
        sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");

        DashboardResponse dashboard = service.dashboard(PERIOD, "RP");

        assertThat(dashboard.courseRevenue()).isEqualByComparingTo("15000.00");
        assertThat(dashboard.assessmentRevenue()).isEqualByComparingTo("1200.00");
        assertThat(dashboard.totalRevenue()).isEqualByComparingTo("16200.00");
    }

    @Test
    void aVoidedReceiptContributesNothingToRevenue() {
        SaleResponse kept = sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");
        SaleResponse cancelled = sellOn(DAY, patientId, null, List.of(courseLine()), "15000.00");
        saleService.voidSale(cancelled.id(), new SaleVoidRequest("ลูกค้าขอยกเลิก"));

        DashboardResponse dashboard = service.dashboard(PERIOD, "RP");

        assertThat(dashboard.totalRevenue())
                .as("ใบที่ยกเลิกแล้วต้องไม่ถูกนับเป็นรายได้ แม้ sales.total ยังอยู่ในตาราง")
                .isEqualByComparingTo("1200.00");
        assertThat(dashboard.courseRevenue()).isEqualByComparingTo("0.00");
        assertThat(dashboard.receiptsIssued()).isEqualTo(1);
        assertThat(dashboard.receiptsVoided()).isEqualTo(1);
        assertThat(kept.id()).isNotEqualTo(cancelled.id());
    }

    @Test
    void reportsZeroRatherThanNullWhenThereIsNoActivity() {
        DashboardResponse dashboard = service.dashboard(PERIOD, "RP");

        assertThat(dashboard.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(dashboard.uniquePatients()).isZero();
        assertThat(dashboard.visits()).isZero();
        assertThat(dashboard.patientsByGender()).isEmpty();
    }

    @Test
    void categorySharesAddUpToOne() {
        sellOn(DAY, patientId, null, List.of(courseLine(), assessmentLine(1)), "16200.00");

        RevenueReportResponse report = service.revenue(PERIOD, "RP");

        BigDecimal shareSum = report.byCategory().stream()
                .map(RevenueReportResponse.CategoryRevenue::share)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shareSum).isEqualByComparingTo("1.0000");
    }

    // ---------- ช่วงเวลาและโซนเวลา ----------

    @Test
    void dailySeriesCoversEveryDayIncludingDaysWithNoSales() {
        ReportPeriod week = new ReportPeriod(DAY, DAY.plusDays(6));
        sellOn(DAY.plusDays(2), patientId, null, List.of(assessmentLine(1)), "1200.00");

        RevenueReportResponse report = service.revenue(week, "RP");

        assertThat(report.daily()).hasSize(7);
        assertThat(report.daily().get(0).amount()).isEqualByComparingTo("0.00");
        assertThat(report.daily().get(2).amount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void bucketsALateEveningSaleOnTheClinicDayNotTheUtcDay() {
        // 00:30 ของวันที่ 12 ตามเวลาไทย = 17:30Z ของวันที่ 11 ถ้าจัดกลุ่มด้วยวันที่แบบ UTC จะตกไปอยู่ผิดวัน
        SaleResponse sale = sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");
        moveSoldAt(sale.id(), DAY.atTime(0, 30).toInstant(ZoneOffset.ofHours(7)));

        RevenueReportResponse report = service.revenue(PERIOD, "RP");

        assertThat(report.daily()).singleElement()
                .satisfies(day -> assertThat(day.date()).isEqualTo(DAY));
        assertThat(report.daily().get(0).amount())
                .as("ยอดรายวันรวมกันต้องเท่ารายได้รวม ถ้าตกวันผิดจะกลายเป็นศูนย์")
                .isEqualByComparingTo(report.totalRevenue());
    }

    @Test
    void excludesSalesOutsideTheRequestedPeriod() {
        sellOn(DAY.minusDays(1), patientId, null, List.of(assessmentLine(1)), "1200.00");
        sellOn(DAY.plusDays(1), patientId, null, List.of(assessmentLine(1)), "1200.00");

        assertThat(service.dashboard(PERIOD, "RP").totalRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void includesSalesOnBothEdgesOfTheRange() {
        ReportPeriod week = new ReportPeriod(DAY, DAY.plusDays(6));
        sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");
        sellOn(DAY.plusDays(6), patientId, null, List.of(assessmentLine(1)), "1200.00");

        assertThat(service.dashboard(week, "RP").totalRevenue())
                .as("ทั้งวันแรกและวันสุดท้ายต้องนับรวม").isEqualByComparingTo("2400.00");
    }

    @Test
    void rejectsAnInvertedDateRange() {
        assertThatThrownBy(() -> new ReportPeriod(DAY, DAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่มาก่อน");
    }

    @Test
    void rejectsARangeLongerThanTwoYears() {
        assertThatThrownBy(() -> new ReportPeriod(DAY, DAY.plusYears(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่เกิน 2 ปี");
    }

    @Test
    void rejectsAnUnknownBranch() {
        assertThatThrownBy(() -> service.dashboard(PERIOD, "ZZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่พบสาขา");
    }

    // ---------- สาขา ----------

    @Test
    void filtersRevenueByBranch() {
        Branch other = branchRepository.save(new Branch("CM", "02", "สาขาเชียงใหม่"));
        Long otherPatient = registerPatientAt(other.getCode(), "หญิง");
        sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");
        sellAtBranch(DAY, otherPatient, other.getCode(), List.of(assessmentLine(2)), "2400.00");

        assertThat(service.dashboard(PERIOD, "RP").totalRevenue()).isEqualByComparingTo("1200.00");
        assertThat(service.dashboard(PERIOD, "CM").totalRevenue()).isEqualByComparingTo("2400.00");
        assertThat(service.dashboard(PERIOD, null).totalRevenue())
                .as("ไม่ระบุสาขาต้องได้ทุกสาขารวมกัน").isEqualByComparingTo("3600.00");
    }

    @Test
    void breaksRevenueDownByBranch() {
        Branch other = branchRepository.save(new Branch("CM", "02", "สาขาเชียงใหม่"));
        Long otherPatient = registerPatientAt(other.getCode(), "หญิง");
        sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");
        sellAtBranch(DAY, otherPatient, other.getCode(), List.of(assessmentLine(1)), "1200.00");

        RevenueReportResponse report = service.revenue(PERIOD, null);

        assertThat(report.byBranch()).hasSize(2);
        assertThat(report.byBranch()).extracting(RevenueReportResponse.BranchRevenue::branchCode)
                .containsExactly("CM", "RP");
    }

    // ---------- ผู้ป่วยและ Visit ----------

    @Test
    void countsOnlyAppointmentsThePatientActuallyAttended() {
        Long other = registerPatient("หญิง");
        attendedVisit(patientId, 9);
        attendedVisit(patientId, 11);
        attendedVisit(other, 13);
        noShow(other, 15);
        booked(other, 17);

        DashboardResponse dashboard = service.dashboard(PERIOD, "RP");

        assertThat(dashboard.visits()).as("นับเฉพาะนัดที่ผู้ป่วยมาถึงจริง").isEqualTo(3);
        assertThat(dashboard.uniquePatients()).as("ผู้ป่วยคนเดิมมาสองครั้งนับเป็นคนเดียว").isEqualTo(2);
    }

    @Test
    void splitsAttendingPatientsByGender() {
        Long female = registerPatient("หญิง");
        attendedVisit(patientId, 9);
        attendedVisit(female, 11);
        attendedVisit(female, 13);

        DashboardResponse dashboard = service.dashboard(PERIOD, "RP");

        assertThat(dashboard.patientsByGender())
                .extracting(DashboardResponse.GenderBreakdown::gender,
                        DashboardResponse.GenderBreakdown::patients)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("ชาย", 1L),
                        org.assertj.core.api.Assertions.tuple("หญิง", 1L));
        assertThat(dashboard.patientsByGender().stream()
                .mapToLong(DashboardResponse.GenderBreakdown::patients).sum())
                .as("ยอดแยกเพศต้องบวกกันได้เท่าจำนวนผู้ป่วยไม่ซ้ำ")
                .isEqualTo(dashboard.uniquePatients());
    }

    // ---------- รายงานนัดหมาย ----------

    @Test
    void countsAppointmentsByStatus() {
        attendedVisit(patientId, 9);
        attendedVisit(patientId, 11);
        noShow(patientId, 13);
        booked(patientId, 15);

        AppointmentReportResponse report = service.appointments(PERIOD, "RP");

        assertThat(report.appointments()).isEqualTo(4);
        assertThat(report.visits()).as("นับเฉพาะที่มาถึงจริง").isEqualTo(2);
        assertThat(report.byStatus())
                .extracting(AppointmentReportResponse.StatusCount::status,
                        AppointmentReportResponse.StatusCount::appointments)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(AppointmentStatus.COMPLETED, 2L),
                        org.assertj.core.api.Assertions.tuple(AppointmentStatus.NO_SHOW, 1L),
                        org.assertj.core.api.Assertions.tuple(AppointmentStatus.CONFIRMED, 1L));
    }

    @Test
    void measuresNoShowRateAgainstAppointmentsThatCameDueOnly() {
        attendedVisit(patientId, 9);
        attendedVisit(patientId, 11);
        attendedVisit(patientId, 13);
        noShow(patientId, 15);
        // นัดที่ยังไม่ถึงเวลาไม่ควรทำให้อัตราการไม่มาดูดีเกินจริง
        booked(patientId, 17);
        booked(patientId, 19);

        AppointmentReportResponse report = service.appointments(PERIOD, "RP");

        assertThat(report.noShowRate()).as("1 ใน 4 ที่ถึงกำหนด ไม่ใช่ 1 ใน 6")
                .isEqualByComparingTo("0.2500");
    }

    @Test
    void reportsZeroNoShowRateWhenNothingHasComeDue() {
        booked(patientId, 9);

        assertThat(service.appointments(PERIOD, "RP").noShowRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void breaksAppointmentsDownByTherapistWithTheirVisitCount() {
        attendedVisit(patientId, 9);
        noShow(patientId, 11);

        AppointmentReportResponse report = service.appointments(PERIOD, "RP");

        assertThat(report.byTherapist()).singleElement().satisfies(load -> {
            assertThat(load.appointments()).isEqualTo(2);
            assertThat(load.visits()).as("นักกายภาพคนนี้มีคนมาจริงคนเดียว").isEqualTo(1);
            assertThat(load.code()).isNotBlank();
        });
    }

    @Test
    void fillsEveryDayOfTheAppointmentSeries() {
        ReportPeriod week = new ReportPeriod(DAY, DAY.plusDays(3));
        attendedVisit(patientId, 9);

        AppointmentReportResponse report = service.appointments(week, "RP");

        assertThat(report.daily()).hasSize(4);
        assertThat(report.daily().get(0).appointments()).isEqualTo(1);
        assertThat(report.daily().get(0).visits()).isEqualTo(1);
        assertThat(report.daily().get(1).appointments()).isZero();
        assertThat(report.daily().stream()
                .mapToLong(AppointmentReportResponse.DailyCount::appointments).sum())
                .as("ผลรวมรายวันต้องเท่ายอดรวม").isEqualTo(report.appointments());
    }

    // ---------- ยอดขายรายบุคคล ----------

    @Test
    void keepsReceiptsWithoutASalespersonInTheirOwnBucket() {
        Long salesperson = registerSalesperson();
        sellOn(DAY, patientId, salesperson, List.of(assessmentLine(1)), "1200.00");
        sellOn(DAY, patientId, null, List.of(assessmentLine(2)), "2400.00");

        StaffSalesReportResponse report = service.staffSales(PERIOD, "RP");

        assertThat(report.rows()).singleElement()
                .satisfies(row -> assertThat(row.amount()).isEqualByComparingTo("1200.00"));
        assertThat(report.unassigned().amount())
                .as("ใบที่ไม่ระบุผู้ขายต้องไม่หายไปจากรายงาน").isEqualByComparingTo("2400.00");
        assertThat(report.unassigned().receipts()).isEqualTo(1);
    }

    @Test
    void perPersonRowsPlusUnassignedEqualTheBranchTotal() {
        Long first = registerSalesperson();
        Long second = registerSalesperson();
        sellOn(DAY, patientId, first, List.of(courseLine()), "15000.00");
        sellOn(DAY, patientId, second, List.of(assessmentLine(1)), "1200.00");
        sellOn(DAY, patientId, null, List.of(assessmentLine(1)), "1200.00");

        StaffSalesReportResponse report = service.staffSales(PERIOD, "RP");

        BigDecimal sum = report.rows().stream().map(StaffSalesReportResponse.Row::amount)
                .reduce(report.unassigned().amount(), BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(report.branchTotal());
        assertThat(report.branchTotal()).isEqualByComparingTo("17400.00");
        assertThat(report.receipts()).isEqualTo(3);
    }

    @Test
    void ranksSalespeopleByAmountSoldDescending() {
        Long small = registerSalesperson();
        Long big = registerSalesperson();
        sellOn(DAY, patientId, small, List.of(assessmentLine(1)), "1200.00");
        sellOn(DAY, patientId, big, List.of(courseLine()), "15000.00");

        StaffSalesReportResponse report = service.staffSales(PERIOD, "RP");

        assertThat(report.rows()).extracting(StaffSalesReportResponse.Row::staffId)
                .containsExactly(big, small);
    }

    @Test
    void leavesVoidedReceiptsOutOfStaffSales() {
        Long salesperson = registerSalesperson();
        SaleResponse sale = sellOn(DAY, patientId, salesperson, List.of(courseLine()), "15000.00");
        saleService.voidSale(sale.id(), new SaleVoidRequest("ยกเลิก"));

        StaffSalesReportResponse report = service.staffSales(PERIOD, "RP");

        assertThat(report.branchTotal()).isEqualByComparingTo("0.00");
        assertThat(report.rows()).isEmpty();
    }

    // ---------- ยอดคงเหลือคอร์ส ----------

    @Test
    void reportsPurchasedUsedAndRemainingPerCourse() {
        SaleResponse sale = sellOn(DAY, patientId, null, List.of(courseLine()), "15000.00");
        Long purchaseId = sale.items().get(0).coursePurchaseId();
        courseLedgerService.use(new CourseUsageRequest(purchaseId, null, null));
        courseLedgerService.use(new CourseUsageRequest(purchaseId, null, null));

        CourseBalanceReportResponse report = service.courseBalances(PERIOD, "RP", null);

        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.purchased()).isEqualTo(13);
            assertThat(row.used()).isEqualTo(2);
            assertThat(row.remaining()).isEqualTo(11);
            assertThat(row.hn()).isNotBlank();
        });
        assertThat(report.totalPurchasedSessions()).isEqualTo(13);
        assertThat(report.totalUsedSessions()).isEqualTo(2);
        assertThat(report.totalRemainingSessions()).isEqualTo(11);
    }

    @Test
    void purchasedAlwaysEqualsUsedPlusRemainingForAnActiveCourse() {
        SaleResponse sale = sellOn(DAY, patientId, null, List.of(courseLine()), "15000.00");
        courseLedgerService.use(new CourseUsageRequest(sale.items().get(0).coursePurchaseId(), null, null));

        CourseBalanceReportResponse.Row row = service.courseBalances(PERIOD, "RP", null).rows().get(0);

        assertThat(row.used() + row.remaining()).isEqualTo(row.purchased());
    }

    @Test
    void keepsACancelledCourseVisibleButOutOfTheSoldValue() {
        SaleResponse sale = sellOn(DAY, patientId, null, List.of(courseLine()), "15000.00");
        courseLedgerService.use(new CourseUsageRequest(sale.items().get(0).coursePurchaseId(), null, null));
        courseLedgerService.cancel(sale.items().get(0).coursePurchaseId(), new CourseReasonRequest("ยกเลิก"));

        CourseBalanceReportResponse report = service.courseBalances(PERIOD, "RP", null);

        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.used()).as("ครั้งที่ใช้ไปแล้วต้องยังอยู่").isEqualTo(1);
            assertThat(row.remaining()).isZero();
        });
        assertThat(report.totalValue())
                .as("คอร์สที่ถูกยกเลิกไม่ควรถูกนับเป็นมูลค่าที่ขายได้").isEqualByComparingTo("0.00");
    }

    @Test
    void filtersCourseBalancesByPatient() {
        Long other = registerPatient("หญิง");
        sellOn(DAY, patientId, null, List.of(courseLine()), "15000.00");
        sellOn(DAY, other, null, List.of(courseLine()), "15000.00");

        assertThat(service.courseBalances(PERIOD, "RP", patientId).rows()).hasSize(1);
        assertThat(service.courseBalances(PERIOD, "RP", null).rows()).hasSize(2);
    }

    // ---------- helpers ----------

    private SaleRequest.Item courseLine() {
        return new SaleRequest.Item(SaleItemType.COURSE, courseId, 1);
    }

    private SaleRequest.Item assessmentLine(int quantity) {
        return new SaleRequest.Item(SaleItemType.ASSESSMENT, assessmentId, quantity);
    }

    private SaleResponse sellOn(LocalDate date, Long patient, Long salesperson,
                                List<SaleRequest.Item> items, String amount) {
        SaleResponse sale = saleService.create(new SaleRequest(patient, "RP", salesperson, null, items,
                List.of(new SaleRequest.Payment("CASH", new BigDecimal(amount)))));
        moveSoldAt(sale.id(), date.atTime(10, 0).toInstant(ZoneOffset.ofHours(7)));
        movePurchasedAt(sale, date);
        return sale;
    }

    private SaleResponse sellAtBranch(LocalDate date, Long patient, String branchCode,
                                      List<SaleRequest.Item> items, String amount) {
        SaleResponse sale = saleService.create(new SaleRequest(patient, branchCode, null, null, items,
                List.of(new SaleRequest.Payment("CASH", new BigDecimal(amount)))));
        moveSoldAt(sale.id(), date.atTime(10, 0).toInstant(ZoneOffset.ofHours(7)));
        return sale;
    }

    /** ใบเสร็จถูกประทับเวลาปัจจุบันเสมอ เทสจึงต้องเลื่อนเวลาเองเพื่อทดสอบช่วงวันที่ */
    private void moveSoldAt(Long saleId, Instant soldAt) {
        entityManager.flush();
        entityManager.createNativeQuery("update sales set sold_at = ?1 where id = ?2")
                .setParameter(1, soldAt).setParameter(2, saleId).executeUpdate();
        entityManager.clear();
    }

    private void movePurchasedAt(SaleResponse sale, LocalDate date) {
        Instant purchasedAt = date.atTime(10, 0).toInstant(ZoneOffset.ofHours(7));
        for (SaleResponse.SaleItemResponse item : sale.items()) {
            if (item.coursePurchaseId() == null) {
                continue;
            }
            entityManager.flush();
            entityManager.createNativeQuery(
                            "update course_purchases set purchased_at = ?1, expires_at = ?2 where id = ?3")
                    .setParameter(1, purchasedAt)
                    .setParameter(2, purchasedAt.plus(Duration.ofDays(90)))
                    .setParameter(3, item.coursePurchaseId())
                    .executeUpdate();
            entityManager.clear();
        }
    }

    private void attendedVisit(Long patient, int hour) {
        Appointment appointment = newAppointment(patient, hour);
        appointment.markArrived();
        appointment.startService();
        appointment.complete();
        appointmentRepository.save(appointment);
    }

    private void noShow(Long patient, int hour) {
        Appointment appointment = newAppointment(patient, hour);
        appointment.markNoShow();
        appointmentRepository.save(appointment);
    }

    private void booked(Long patient, int hour) {
        appointmentRepository.save(newAppointment(patient, hour));
    }

    private Appointment newAppointment(Long patient, int hour) {
        Branch branch = branchRepository.findByCodeAndActiveTrue("RP").orElseThrow();
        // ใช้นักกายภาพที่ migration seed ไว้ Therapist ไม่มี constructor สาธารณะให้สร้างใหม่
        // ทุกนัดในเทสนี้อยู่คนละชั่วโมงกัน จึงไม่ชน EXCLUDE constraint ของ Postgres
        Therapist therapist = therapistRepository.findAll().stream()
                .filter(candidate -> candidate.getBranch().getCode().equals("RP"))
                .findFirst().orElseThrow();
        ServiceItem item = serviceRepository.findById(assessmentId).orElseThrow();
        Instant start = DAY.atTime(hour, 0).toInstant(ZoneOffset.ofHours(7));
        return new Appointment(branch, patientRepository.findById(patient).orElseThrow(),
                therapist, item, start, start.plus(Duration.ofMinutes(45)), null);
    }

    private Long registerSalesperson() {
        int unique = UNIQUE.incrementAndGet();
        AppUser user = userRepository.save(new AppUser("report-sp-" + unique, "hash", Role.STAFF));
        return staffRepository.save(new Staff(user, "RSP" + unique, "ผู้ขาย ทดสอบ", "0800000030")).getId();
    }

    private Long registerPatient(String gender) {
        return registerPatientAt("RP", gender);
    }

    private Long registerPatientAt(String branchCode, String gender) {
        int unique = UNIQUE.incrementAndGet();
        Patient patient = patientRepository.findById(registrationService.register(new PatientRegistrationRequest(
                branchCode, "report-" + unique, "report-" + unique + "@example.com", "password123", "นาย",
                "ทดสอบ รายงาน", LocalDate.of(1990, 1, 1), gender, "0800000031",
                "NATIONAL_ID", "1100000003" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id()).orElseThrow();
        return patient.getId();
    }
}
