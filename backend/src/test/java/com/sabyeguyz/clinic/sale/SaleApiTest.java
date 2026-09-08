package com.sabyeguyz.clinic.sale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleApiTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CourseRepository courseRepository;
    @Autowired ServiceItemRepository serviceRepository;
    @Autowired PatientRegistrationService registrationService;

    private Long patientId;
    private Long courseId;
    private Long assessmentServiceId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        courseId = courseRepository.save(new Course(
                "SAPI" + UNIQUE.incrementAndGet(), "คอร์สทดสอบใบเสร็จ",
                new BigDecimal("9000.00"), 5, 1, 60)).getId();
        assessmentServiceId = serviceRepository.findByCode("ASSESSMENT").orElseThrow().getId();
    }

    @Test
    void issuesAReceiptAndReturnsLocation() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseSaleBody("9000.00")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.receiptNo").value(org.hamcrest.Matchers.matchesPattern("RC\\d{10}")))
                .andExpect(jsonPath("$.total").value(9000.00))
                .andExpect(jsonPath("$.paid").value(9000.00))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.patient.hn").isNotEmpty())
                .andExpect(jsonPath("$.items[0].coursePurchaseId").isNotEmpty());
    }

    @Test
    void rejectsAReceiptWithNoItems() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":%d,"branchCode":"RP","items":[],
                                 "payments":[{"paymentMethodCode":"CASH","amount":100}]}
                                """.formatted(patientId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.items").exists());
    }

    @Test
    void rejectsPaymentsThatDoNotMatchTheTotal() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseSaleBody("8000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ไม่ตรงกับยอดที่ต้องชำระ")));
    }

    @Test
    void returnsNotFoundForAnUnknownPatient() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":999999,"branchCode":"RP",
                                 "items":[{"type":"COURSE","referenceId":%d,"quantity":1}],
                                 "payments":[{"paymentMethodCode":"CASH","amount":9000.00}]}
                                """.formatted(courseId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsTheReceiptWithItemsAndPayments() throws Exception {
        long saleId = createSale(courseSaleBody("9000.00"));

        mockMvc.perform(get("/api/sales/{id}", saleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("COURSE"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.payments[0].paymentMethodCode").value("CASH"));
    }

    @Test
    void voidsAReceiptAndNetsTheRevenueToZero() throws Exception {
        long saleId = createSale(courseSaleBody("9000.00"));

        mockMvc.perform(post("/api/sales/{id}/void", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ลูกค้าขอยกเลิก\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.paid").value(0))
                .andExpect(jsonPath("$.voidReason").value("ลูกค้าขอยกเลิก"))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[1].type").value("REFUND"))
                .andExpect(jsonPath("$.payments[1].amount").value(-9000.00));
    }

    @Test
    void rejectsVoidWithoutAReason() throws Exception {
        long saleId = createSale(courseSaleBody("9000.00"));

        mockMvc.perform(post("/api/sales/{id}/void", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void returnsConflictWhenVoidingTwice() throws Exception {
        long saleId = createSale(courseSaleBody("9000.00"));
        mockMvc.perform(post("/api/sales/{id}/void", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ครั้งแรก\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sales/{id}/void", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ครั้งที่สอง\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returnsNotFoundForAnUnknownReceipt() throws Exception {
        mockMvc.perform(get("/api/sales/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsTheReceiptsOfAPatient() throws Exception {
        createSale(courseSaleBody("9000.00"));

        mockMvc.perform(get("/api/patients/{id}/sales", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].description").value("คอร์สทดสอบใบเสร็จ"));
    }

    @Test
    void theCourseSoldOnTheReceiptShowsUpOnThePatientsCourseList() throws Exception {
        createSale(courseSaleBody("9000.00"));

        mockMvc.perform(get("/api/patients/{id}/courses", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].remaining").value(6))
                .andExpect(jsonPath("$[0].usable").value(true));
    }

    private long createSale(String body) throws Exception {
        String response = mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }

    private String courseSaleBody(String amount) {
        return """
                {"patientId":%d,"branchCode":"RP",
                 "items":[{"type":"COURSE","referenceId":%d,"quantity":1}],
                 "payments":[{"paymentMethodCode":"CASH","amount":%s}]}
                """.formatted(patientId, courseId, amount);
    }

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "RP", "sale-api-" + unique, "sale-api-" + unique + "@example.com", "password123", "นาย",
                "ทดสอบ ใบเสร็จ API", LocalDate.of(1990, 1, 1), "ชาย", "0800000015",
                "NATIONAL_ID", "1100000005" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
