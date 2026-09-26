package com.sabyeguyz.clinic.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.patient.PatientRegistrationRequest;
import com.sabyeguyz.clinic.patient.PatientRegistrationService;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class AppointmentApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired AppointmentBookingService bookingService;
    @Autowired PatientRegistrationService registrationService;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;

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
    void createsAppointment() throws Exception {
        mockMvc.perform(postAppointment(body(nineAmTomorrow)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.branchCode").value("01"))
                .andExpect(jsonPath("$.patient.hn").isNotEmpty())
                .andExpect(jsonPath("$.service.durationMinutes").value(60));
    }

    @Test
    void rejectsOverlappingAppointmentWithConflict() throws Exception {
        mockMvc.perform(postAppointment(body(nineAmTomorrow)))
                .andExpect(status().isCreated());

        mockMvc.perform(postAppointment(body(nineAmTomorrow.plusMinutes(30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void allowsBackToBackAppointment() throws Exception {
        mockMvc.perform(postAppointment(body(nineAmTomorrow)))
                .andExpect(status().isCreated());

        mockMvc.perform(postAppointment(body(nineAmTomorrow.plusHours(1))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMissingPatientId() throws Exception {
        String invalid = """
                {"branchCode":"01","therapistId":%d,"serviceId":%d,"startAt":"%s"}
                """.formatted(therapistId, serviceId, nineAmTomorrow);

        mockMvc.perform(postAppointment(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.patientId").exists());
    }

    @Test
    void rejectsUnknownTherapistWithBadRequest() throws Exception {
        String invalid = """
                {"branchCode":"01","patientId":%d,"therapistId":999999,"serviceId":%d,"startAt":"%s"}
                """.formatted(patientId, serviceId, nineAmTomorrow);

        mockMvc.perform(postAppointment(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsMalformedStartAt() throws Exception {
        String invalid = """
                {"branchCode":"01","patientId":%d,"therapistId":%d,"serviceId":%d,"startAt":"1 กันยายน 2026"}
                """.formatted(patientId, therapistId, serviceId);

        mockMvc.perform(postAppointment(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void documentsAppointmentContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/appointments'].post.tags[0]").value("Appointments"))
                .andExpect(jsonPath("$.paths['/api/appointments'].post.responses['409'].content['application/json'].schema").exists())
                .andExpect(jsonPath("$.components.schemas.AppointmentCreateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.AppointmentResponse").exists());
    }

    @Test
    void reschedulesAppointmentViaPatch() throws Exception {
        long id = bookTomorrowNineAm();

        mockMvc.perform(patch("/api/appointments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + tomorrowAt(14) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").exists());
    }

    @Test
    void rejectsRescheduleWithConflict() throws Exception {
        long firstId = bookTomorrowNineAm();
        mockMvc.perform(postAppointment(body(tomorrowAt(14))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/appointments/{id}", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + tomorrowAt(14) + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRescheduleAfterCheckIn() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id));

        mockMvc.perform(patch("/api/appointments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + tomorrowAt(14) + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundWhenReschedulingMissingAppointment() throws Exception {
        mockMvc.perform(patch("/api/appointments/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + tomorrowAt(14) + "\"}"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAppointment(String body) {
        return post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String body(OffsetDateTime startAt) {
        return """
                {"branchCode":"01","patientId":%d,"therapistId":%d,"serviceId":%d,"startAt":"%s","note":"นัดทดสอบ"}
                """.formatted(patientId, therapistId, serviceId, startAt);
    }

    private long bookTomorrowNineAm() {
        return bookingService.create(
                new AppointmentCreateRequest("01", patientId, therapistId, serviceId, nineAmTomorrow, null)).id();
    }

    private OffsetDateTime tomorrowAt(int hour) {
        return OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(1).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    private Long registerPatient() {
        return registrationService.register(new PatientRegistrationRequest(
                "01", "api-test", "api-test@example.com", "password123", "นาย",
                "ทดสอบ API นัดหมาย", LocalDate.of(1990, 1, 1), "ชาย", "0800000002",
                "NATIONAL_ID", "1100000000997", "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}