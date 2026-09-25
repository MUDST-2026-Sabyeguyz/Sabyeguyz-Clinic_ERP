package com.sabyeguyz.clinic.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseRepository;
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
class CourseLedgerApiTest {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired CourseRepository courseRepository;
    @Autowired PatientRegistrationService registrationService;

    private Long patientId;
    private Long courseId;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        courseId = courseRepository.save(new Course(
                "API" + UNIQUE.incrementAndGet(), "คอร์สทดสอบ API", new BigDecimal("9000.00"), 5, 1, 60)).getId();
    }

    @Test
    void purchasesCourseAndReturnsLocation() throws Exception {
        mockMvc.perform(post("/api/course-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.purchased").value(6))
                .andExpect(jsonPath("$.remaining").value(6))
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.usable").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.patient.hn").isNotEmpty());
    }

    @Test
    void rejectsPurchaseWithoutPatientId() throws Exception {
        mockMvc.perform(post("/api/course-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":%d,\"branchCode\":\"01\"}".formatted(courseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.patientId").exists());
    }

    @Test
    void returnsNotFoundForUnknownPatient() throws Exception {
        mockMvc.perform(post("/api/course-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":999999,\"courseId\":%d,\"branchCode\":\"01\"}".formatted(courseId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void consumesSessionAndExposesTheLedger() throws Exception {
        long purchaseId = purchase();

        mockMvc.perform(post("/api/course-usages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coursePurchaseId\":%d,\"note\":\"ครั้งที่ 1\"}".formatted(purchaseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.used").value(1))
                .andExpect(jsonPath("$.remaining").value(5));

        mockMvc.perform(get("/api/course-purchases/{id}", purchaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movements.length()").value(2))
                .andExpect(jsonPath("$.movements[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$.movements[0].balanceAfter").value(6))
                .andExpect(jsonPath("$.movements[1].type").value("USAGE"))
                .andExpect(jsonPath("$.movements[1].balanceAfter").value(5));
    }

    @Test
    void returnsConflictWhenCourseIsExhausted() throws Exception {
        long purchaseId = purchase();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/course-usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"coursePurchaseId\":%d}".formatted(purchaseId)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/course-usages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coursePurchaseId\":%d}".formatted(purchaseId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void listsCoursesOfPatient() throws Exception {
        purchase();

        mockMvc.perform(get("/api/patients/{id}/courses", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].course.name").value("คอร์สทดสอบ API"));
    }

    @Test
    void cancelsCourseWithReason() throws Exception {
        long purchaseId = purchase();

        mockMvc.perform(post("/api/course-purchases/{id}/cancel", purchaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ผู้ป่วยขอยกเลิก\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void rejectsCancelWithoutReason() throws Exception {
        long purchaseId = purchase();

        mockMvc.perform(post("/api/course-purchases/{id}/cancel", purchaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void returnsNotFoundForUnknownCoursePurchase() throws Exception {
        mockMvc.perform(get("/api/course-purchases/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    private long purchase() throws Exception {
        String body = mockMvc.perform(post("/api/course-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*$", "$1"));
    }

    private String purchaseBody() {
        return "{\"patientId\":%d,\"courseId\":%d,\"branchCode\":\"01\"}".formatted(patientId, courseId);
    }

    private Long registerPatient() {
        int unique = UNIQUE.incrementAndGet();
        return registrationService.register(new PatientRegistrationRequest(
                "01", "course-api-" + unique, "course-api-" + unique + "@example.com", "password123", "นาย",
                "ทดสอบ คอร์ส API", LocalDate.of(1990, 1, 1), "ชาย", "0800000012",
                "NATIONAL_ID", "1100000007" + String.format("%03d", unique), "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
