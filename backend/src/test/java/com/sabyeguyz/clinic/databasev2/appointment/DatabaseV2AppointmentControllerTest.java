package com.sabyeguyz.clinic.databasev2.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.catalog.DatabaseV2ServiceRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.patient.DatabaseV2PatientRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class DatabaseV2AppointmentControllerTest {

    private DatabaseV2AppointmentRepository appointmentRepository;
    private DatabaseV2PatientRepository patientRepository;
    private DatabaseV2BranchRepository branchRepository;
    private DatabaseV2StaffRepository staffRepository;
    private DatabaseV2ServiceRepository serviceRepository;
    private CurrentActorProvider actorProvider;
    private DatabaseV2AppointmentController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(DatabaseV2AppointmentRepository.class);
        patientRepository = mock(DatabaseV2PatientRepository.class);
        branchRepository = mock(DatabaseV2BranchRepository.class);
        staffRepository = mock(DatabaseV2StaffRepository.class);
        serviceRepository = mock(DatabaseV2ServiceRepository.class);
        actorProvider = mock(CurrentActorProvider.class);
        auth = mock(Authentication.class);

        controller = new DatabaseV2AppointmentController(
                appointmentRepository, patientRepository, branchRepository,
                staffRepository, serviceRepository, actorProvider, mock(com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard.class)
        );

        when(actorProvider.getActorIdOrDefault(any(), any())).thenReturn(1L);

        DatabaseV2Branch branch = new DatabaseV2Branch(1L, "01", "Main Branch", "123 St", "021234567", true);
        when(branchRepository.findByCode("01")).thenReturn(Optional.of(branch));

        DatabaseV2PatientRepository.PatientRecord patient = mock(DatabaseV2PatientRepository.PatientRecord.class);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));

        DatabaseV2StaffRepository.StaffModel staff = new DatabaseV2StaffRepository.StaffModel(
                20L, "ST-01", "หมอ", "ใจดี", "Therapist", 1L, "01", "Main Branch",
                true, LocalDate.now(), null, null, 1L, "therapist", "THERAPIST"
        );
        when(staffRepository.findById(20L)).thenReturn(Optional.of(staff));

        DatabaseV2ServiceRepository.ServiceModel service = new DatabaseV2ServiceRepository.ServiceModel(
                30L, "SRV-01", "Physical Therapy 60", "SingleVisit", new BigDecimal("1000"), true, null, null, null
        );
        when(serviceRepository.findForBooking(30L)).thenReturn(Optional.of(service));
    }

    private DatabaseV2AppointmentRepository.AppointmentModel mockAppointment(Long id, String status) {
        return new DatabaseV2AppointmentRepository.AppointmentModel(
                id, 10L, "2601090001", "สมชาย ใจดี", 1L, "01", 20L, "หมอ ใจดี",
                30L, "Physical Therapy 60", 60, null, null,
                LocalDate.of(2026, 9, 25), LocalTime.of(10, 0), status,
                LocalDateTime.now(), null, "First visit"
        );
    }

    @Test
    void createAppointmentSuccess() {
        Instant startAt = Instant.parse("2026-09-25T03:00:00Z"); // 10:00 Asia/Bangkok
        when(appointmentRepository.hasIntervalConflict(eq(20L), any(), any(LocalDate.class), any(LocalTime.class), eq(60), any())).thenReturn(false);

        DatabaseV2AppointmentRepository.AppointmentModel model = mockAppointment(100L, "Confirmed");
        when(appointmentRepository.create(eq(10L), eq(1L), eq(20L), eq(30L), any(), any(), any(), eq("First visit"), eq(1L)))
                .thenReturn(model);

        DatabaseV2AppointmentController.CreateRequest req = new DatabaseV2AppointmentController.CreateRequest(
                "01", 10L, 20L, 30L, startAt, "First visit", null
        );

        ResponseEntity<DatabaseV2AppointmentController.AppointmentResponse> response = controller.create(req, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/appointments/100");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().note()).isEqualTo("First visit");
    }

    @Test
    void createAppointmentRejectsOnConflict() {
        Instant startAt = Instant.parse("2026-09-25T03:00:00Z");
        when(appointmentRepository.hasIntervalConflict(eq(20L), any(), any(LocalDate.class), any(LocalTime.class), eq(60), any())).thenReturn(true);

        DatabaseV2AppointmentController.CreateRequest req = new DatabaseV2AppointmentController.CreateRequest(
                "01", 10L, 20L, 30L, startAt, null, null
        );

        assertThatThrownBy(() -> controller.create(req, auth))
                .isInstanceOf(DatabaseV2ConflictException.class)
                .hasMessageContaining("ช่วงเวลานี้มีนัดหมายอื่นอยู่แล้ว");
    }

    @Test
    void checkInSuccessCreatesPatientVisit() {
        DatabaseV2AppointmentRepository.AppointmentModel confirmed = mockAppointment(100L, "Confirmed");
        DatabaseV2AppointmentRepository.AppointmentModel arrived = mockAppointment(100L, "Arrived");

        when(appointmentRepository.findForUpdate(100L)).thenReturn(Optional.of(confirmed));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(arrived));

        DatabaseV2AppointmentController.AppointmentResponse res = controller.checkIn(100L, auth);

        verify(appointmentRepository).updateStatus(100L, "Confirmed", "Arrived", "Patient arrived", 1L);
        verify(appointmentRepository).createPatientVisit(100L, 10L, 1L, 20L, 1L);
        assertThat(res.status()).isEqualTo("ARRIVED");
    }

    @Test
    void startServiceSuccess() {
        DatabaseV2AppointmentRepository.AppointmentModel arrived = mockAppointment(100L, "Arrived");
        DatabaseV2AppointmentRepository.AppointmentModel inService = mockAppointment(100L, "InService");

        when(appointmentRepository.findForUpdate(100L)).thenReturn(Optional.of(arrived));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(inService));

        DatabaseV2AppointmentController.AppointmentResponse res = controller.startService(100L, auth);

        verify(appointmentRepository).updateStatus(100L, "Arrived", "InService", "Service started", 1L);
        verify(appointmentRepository).updateVisitStatus(100L, "InService", 1L);
        assertThat(res.status()).isEqualTo("IN_SERVICE");
    }

    @Test
    void completeSuccess() {
        DatabaseV2AppointmentRepository.AppointmentModel inService = mockAppointment(100L, "InService");
        DatabaseV2AppointmentRepository.AppointmentModel completed = mockAppointment(100L, "Completed");

        when(appointmentRepository.findForUpdate(100L)).thenReturn(Optional.of(inService));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(completed));

        DatabaseV2AppointmentController.AppointmentResponse res = controller.complete(100L, auth);

        verify(appointmentRepository).updateStatus(100L, "InService", "Completed", "Service completed", 1L);
        verify(appointmentRepository).updateVisitStatus(100L, "Completed", 1L);
        assertThat(res.status()).isEqualTo("COMPLETED");
    }

    @Test
    void cancelRejectsIfCompleted() {
        DatabaseV2AppointmentRepository.AppointmentModel completed = mockAppointment(100L, "Completed");
        when(appointmentRepository.findForUpdate(100L)).thenReturn(Optional.of(completed));

        DatabaseV2AppointmentController.CancelRequest req = new DatabaseV2AppointmentController.CancelRequest("Changed mind");

        assertThatThrownBy(() -> controller.cancel(100L, req, auth))
                .isInstanceOf(DatabaseV2ConflictException.class)
                .hasMessageContaining("ไม่สามารถยกเลิกนัดหมาย");
    }

    @Test
    void searchReturnsPage() {
        DatabaseV2AppointmentRepository.AppointmentModel m = mockAppointment(100L, "Confirmed");
        when(appointmentRepository.search(eq("01"), any(), any(), any(), eq(20), eq(0)))
                .thenReturn(List.of(m));
        when(appointmentRepository.countSearch(eq("01"), any(), any(), any())).thenReturn(1);

        DatabaseV2AppointmentController.PageResponse page = controller.search("01", null, null, null, 0, 20);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(100L);
    }
}
