package com.sabyeguyz.clinic.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AppointmentLifecycleApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired AppointmentBookingService bookingService;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;
    @Autowired PatientRegistrationService registrationService;

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
    void checksInConfirmedAppointment() throws Exception {
        long id = bookTomorrowNineAm();

        mockMvc.perform(post("/api/appointments/{id}/check-in", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));
    }

    @Test
    void startsServiceAfterCheckIn() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/{id}/start-service", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_SERVICE"));
    }

    @Test
    void rejectsStartServiceBeforeCheckInWithConflict() throws Exception {
        long id = bookTomorrowNineAm();

        mockMvc.perform(post("/api/appointments/{id}/start-service", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void completesAppointmentAfterServiceStarted() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/{id}/start-service", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void rejectsCompleteBeforeServiceStartedWithConflict() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/{id}/complete", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void marksAppointmentAsNoShow() throws Exception {
        long id = bookTomorrowNineAm();

        mockMvc.perform(post("/api/appointments/{id}/no-show", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void rejectsNoShowAfterPatientArrivedWithConflict() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/{id}/no-show", id))
                .andExpect(status().isConflict());
    }

    @Test
    void releasesTimeSlotAfterNoShow() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/no-show", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(nineAmTomorrow)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsSecondCheckInWithConflict() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/{id}/check-in", id))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelsAppointmentWithReason() throws Exception {
        long id = bookTomorrowNineAm();

        mockMvc.perform(post("/api/appointments/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ผู้ป่วยติดธุระ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("ผู้ป่วยติดธุระ"));
    }

    @Test
    void rejectsCancelWithoutReason() throws Exception {
        long id = bookTomorrowNineAm();

        mockMvc.perform(post("/api/appointments/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void rejectsCancelOfCompletedAppointmentWithConflict() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/check-in", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/{id}/start-service", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/{id}/complete", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"เปลี่ยนใจ\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void releasesTimeSlotAfterCancellation() throws Exception {
        long id = bookTomorrowNineAm();
        mockMvc.perform(post("/api/appointments/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ผู้ป่วยยกเลิก\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(nineAmTomorrow)))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsNotFoundWhenCheckingInMissingAppointment() throws Exception {
        mockMvc.perform(post("/api/appointments/{id}/check-in", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ไม่พบการนัดหมาย id 999999"));
    }

    @Test
    void returnsNotFoundWhenCancellingMissingAppointment() throws Exception {
        mockMvc.perform(post("/api/appointments/{id}/cancel", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ทดสอบ\"}"))
                .andExpect(status().isNotFound());
    }

    private long bookTomorrowNineAm() {
        return bookingService.create(
                new AppointmentCreateRequest("RP", patientId, therapistId, serviceId, nineAmTomorrow, null)).id();
    }

    private String body(OffsetDateTime startAt) {
        return """
                {"branchCode":"RP","patientId":%d,"therapistId":%d,"serviceId":%d,"startAt":"%s","note":"นัดทดสอบ"}
                """.formatted(patientId, therapistId, serviceId, startAt);
    }

    private Long registerPatient() {
        return registrationService.register(new PatientRegistrationRequest(
                "RP", "lifecycle-test", "lifecycle-test@example.com", "password123", "นาย",
                "ทดสอบ สถานะนัด", LocalDate.of(1990, 1, 1), "ชาย", "0800000004",
                "NATIONAL_ID", "1100000000995", "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}
