package com.sabyeguyz.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentQueryServiceTest {
    @Autowired AppointmentQueryService queryService;
    @Autowired AppointmentBookingService bookingService;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired TherapistRepository therapistRepository;
    @Autowired ServiceItemRepository serviceItemRepository;
    @Autowired PatientRegistrationService registrationService;
    @Autowired MockMvc mockMvc;

    private Long patientId;
    private Long therapistOneId;
    private Long therapistTwoId;
    private Long serviceId;
    private OffsetDateTime nineAmTomorrow;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        patientId = registerPatient();
        therapistOneId = therapistId("RP-T01");
        therapistTwoId = therapistId("RP-T02");
        serviceId = serviceItemRepository.findAll().stream()
                .filter(s -> "PHYSIO_60".equals(s.getCode())).findFirst().orElseThrow().getId();
        nineAmTomorrow = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        tomorrow = nineAmTomorrow.toLocalDate();

        book(therapistOneId, nineAmTomorrow);
        book(therapistOneId, nineAmTomorrow.plusHours(2));
        book(therapistTwoId, nineAmTomorrow);
    }

    @Test
    void returnsAppointmentsOfTheDaySortedByStartTime() {
        AppointmentSearchResponse response = queryService.search("01", tomorrow, null, null, 0, 20);

        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.items()).extracting(AppointmentResponse::startAt).isSorted();
    }

    @Test
    void filtersByTherapist() {
        AppointmentSearchResponse response = queryService.search("01", tomorrow, therapistTwoId, null, 0, 20);

        assertThat(response.totalItems()).isEqualTo(1);
        assertThat(response.items().get(0).therapist().id()).isEqualTo(therapistTwoId);
    }

    @Test
    void filtersByStatus() {
        Appointment cancelled = appointmentRepository.findAll().get(0);
        cancelled.cancel("ผู้ป่วยแจ้งยกเลิก");
        appointmentRepository.saveAndFlush(cancelled);

        assertThat(queryService.search("01", tomorrow, null, AppointmentStatus.CANCELLED, 0, 20).totalItems())
                .isEqualTo(1);
        assertThat(queryService.search("01", tomorrow, null, AppointmentStatus.CONFIRMED, 0, 20).totalItems())
                .isEqualTo(2);
    }

    @Test
    void returnsEmptyPageForDayWithoutAppointments() {
        AppointmentSearchResponse response = queryService.search("01", tomorrow.plusDays(30), null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void paginatesInDatabase() {
        AppointmentSearchResponse response = queryService.search("01", tomorrow, null, null, 1, 2);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> queryService.search(null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queryService.search(null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownBranchCode() {
        assertThatThrownBy(() -> queryService.search("ZZ", tomorrow, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่พบสาขา");
    }

    @Test
    void returnsAppointmentById() {
        Long id = appointmentRepository.findAll().get(0).getId();

        assertThat(queryService.getById(id).id()).isEqualTo(id);
    }

    @Test
    void throwsWhenAppointmentDoesNotExist() {
        assertThatThrownBy(() -> queryService.getById(999999L))
                .isInstanceOf(AppointmentNotFoundException.class)
                .hasMessageContaining("999999");
    }

    // AppointmentQueryApiTest
    @Test
    void returnsPagedAppointments() throws Exception {
        mockMvc.perform(get("/api/appointments")
                        .param("branchCode", "01")
                        .param("date", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].patient.hn").isNotEmpty());
    }

    @Test
    void returnsEmptyPageWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/appointments").param("date", tomorrow.plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void rejectsInvalidStatusValue() throws Exception {
        mockMvc.perform(get("/api/appointments").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/appointments").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingAppointment() throws Exception {
        mockMvc.perform(get("/api/appointments/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ไม่พบการนัดหมาย id 999999"));
    }
    //

    private void book(Long therapistId, OffsetDateTime startAt) {
        bookingService.create(new AppointmentCreateRequest("01", patientId, therapistId, serviceId, startAt, null));
    }

    private Long therapistId(String code) {
        return therapistRepository.findAll().stream()
                .filter(t -> code.equals(t.getCode())).findFirst().orElseThrow().getId();
    }

    private Long registerPatient() {
        return registrationService.register(new PatientRegistrationRequest(
                "01", "query-appt", "query-appt@example.com", "password123", "นาย",
                "ทดสอบ ค้นหานัด", LocalDate.of(1990, 1, 1), "ชาย", "0800000003",
                "NATIONAL_ID", "1100000000996", "กรุงเทพฯ", false, false, "",
                "ผู้ติดต่อ", "ญาติ", "0899999999", true)).id();
    }
}