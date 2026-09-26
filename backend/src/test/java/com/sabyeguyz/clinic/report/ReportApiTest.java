package com.sabyeguyz.clinic.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.sale.SaleItemType;
import com.sabyeguyz.clinic.sale.SaleRequest;
import com.sabyeguyz.clinic.sale.SaleResponse;
import com.sabyeguyz.clinic.sale.SaleService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();
    private static final LocalDate DAY = LocalDate.of(2032, 7, 4);

    @Autowired MockMvc mockMvc;
    @Autowired SaleService saleService;
    @Autowired CourseRepository courseRepository;
    @Autowired ServiceItemRepository serviceRepository;
    @Autowired PatientRegistrationService registrationService;
    @PersistenceContext EntityManager entityManager;

    private Long patientId;
    private Long courseId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        courseId = courseRepository.save(new Course("RAPI" + UNIQUE.incrementAndGet(), "คอร์สรายงาน API",
                new BigDecimal("9000.00"), 5, 1, 60)).getId();
    }

    @Test
    void returnsTheDashboardForAPeriod() throws Exception {
        sell();

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("from", DAY.toString()).param("to", DAY.toString()).param("branchCode", "01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(DAY.toString()))
                .andExpect(jsonPath("$.branchCode").value("01"))
                .andExpect(jsonPath("$.totalRevenue").value(9000.00))
                .andExpect(jsonPath("$.courseRevenue").value(9000.00))
                .andExpect(jsonPath("$.singleVisitRevenue").value(0))
                .andExpect(jsonPath("$.receiptsIssued").value(1))
                .andExpect(jsonPath("$.receiptsVoided").value(0));
    }

    @Test
    void returnsTheRevenueReportWithADailySeries() throws Exception {
        sell();

        mockMvc.perform(get("/api/reports/revenue")
                        .param("from", DAY.toString()).param("to", DAY.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(9000.00))
                .andExpect(jsonPath("$.daily.length()").value(3))
                .andExpect(jsonPath("$.daily[0].date").value(DAY.toString()))
                .andExpect(jsonPath("$.daily[0].amount").value(9000.00))
                .andExpect(jsonPath("$.daily[1].amount").value(0))
                .andExpect(jsonPath("$.byCategory[?(@.type=='COURSE')].amount").value(9000.00));
    }

    @Test
    void returnsTheCourseBalanceReport() throws Exception {
        sell();

        mockMvc.perform(get("/api/reports/course-balances")
                        .param("from", DAY.toString()).param("to", DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchases").value(1))
                .andExpect(jsonPath("$.totalPurchasedSessions").value(6))
                .andExpect(jsonPath("$.totalRemainingSessions").value(6))
                .andExpect(jsonPath("$.rows[0].courseName").value("คอร์สรายงาน API"))
                .andExpect(jsonPath("$.rows[0].status").value("ACTIVE"));
    }

    @Test
    void returnsTheStaffSalesReportWithAnUnassignedBucket() throws Exception {
        sell();

        mockMvc.perform(get("/api/reports/staff-sales")
                        .param("from", DAY.toString()).param("to", DAY.toString()).param("branchCode", "01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchTotal").value(9000.00))
                .andExpect(jsonPath("$.rows.length()").value(0))
                .andExpect(jsonPath("$.unassigned.amount").value(9000.00))
                .andExpect(jsonPath("$.unassigned.fullName").value("ไม่ระบุผู้ขาย"));
    }

    @Test
    void returnsTheAppointmentReport() throws Exception {
        mockMvc.perform(get("/api/reports/appointments")
                        .param("from", DAY.toString()).param("to", DAY.toString()).param("branchCode", "01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments").value(0))
                .andExpect(jsonPath("$.visits").value(0))
                .andExpect(jsonPath("$.noShowRate").value(0))
                .andExpect(jsonPath("$.daily.length()").value(1));
    }

    @Test
    void rejectsAnInvertedRange() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .param("from", DAY.toString()).param("to", DAY.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsAnUnknownBranch() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .param("from", DAY.toString()).param("to", DAY.toString()).param("branchCode", "ZZ"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMalformedDate() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .param("from", "4 กรกฎาคม").param("to", DAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMissingDate() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").param("to", DAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForAnUnknownPatientFilter() throws Exception {
        mockMvc.perform(get("/api/reports/course-balances")
                        .param("from", DAY.toString()).param("to", DAY.toString())
                        .param("patientId", "999999"))
                .andExpect(status().isNotFound());
    }

    private void sell() {
        SaleResponse sale = saleService.create(new SaleRequest(patientId, "01", null, null,
                List.of(new SaleRequest.Item(SaleItemType.COURSE, courseId, 1)),
                List.of(new SaleRequest.Payment("CASH", new BigDecimal("9000.00")))));
        entityManager.flush();
        entityManager.createNativeQuery("update sales set sold_at = ?1 where id = ?2")
                .setParameter(1, DAY.atTime(10, 0).toInstant(ZoneOffset.ofHours(7)))
                .setParameter(2, sale.id()).executeUpdate();
        // ต้องเลื่อน expires_at ตามไปด้วย ไม่งั้นชน chk_course_purchases_period ที่บังคับว่าวันหมดอายุต้องอยู่หลังวันซื้อ
        Instant purchasedAt = DAY.atTime(10, 0).toInstant(ZoneOffset.ofHours(7));
        entityManager.createNativeQuery(
                        "update course_purchases set purchased_at = ?1, expires_at = ?2 where id = ?3")
                .setParameter(1, purchasedAt)
                .setParameter(2, purchasedAt.plus(Duration.ofDays(60)))
                .setParameter(3, sale.items().get(0).coursePurchaseId()).executeUpdate();
        entityManager.clear();
    }

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "01", "report-api-" + unique, "report-api-" + unique + "@example.com", "password123", "นาย",
                "ทดสอบ รายงาน API", LocalDate.of(1990, 1, 1), "ชาย", "0800000032",
                "NATIONAL_ID", "1100000002" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
