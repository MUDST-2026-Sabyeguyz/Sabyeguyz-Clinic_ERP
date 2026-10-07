package com.sabyeguyz.clinic.databasev2.treatment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class DatabaseV2TreatmentControllerTest {

    private DatabaseV2TreatmentRepository treatmentRepository;
    private CurrentActorProvider actorProvider;
    private DatabaseV2TreatmentController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        treatmentRepository = mock(DatabaseV2TreatmentRepository.class);
        actorProvider = mock(CurrentActorProvider.class);
        auth = mock(Authentication.class);
        controller = new DatabaseV2TreatmentController(treatmentRepository, actorProvider);

        when(actorProvider.getActorIdOrDefault(any(), any())).thenReturn(1L);
    }

    @Test
    void serviceListReturnsActiveServices() {
        when(treatmentRepository.findActiveServices()).thenReturn(List.of(
                new DatabaseV2TreatmentRepository.ServiceOptionRecord(1L, "ACU-01", "Acupuncture", 60, new BigDecimal("1200.00"))
        ));

        List<DatabaseV2TreatmentController.ServiceResponse> result = controller.serviceList();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("ACU-01");
        assertThat(result.get(0).price()).isEqualByComparingTo("1200.00");
    }

    @Test
    void listReturnsTreatmentsForPatient() {
        when(treatmentRepository.findByPatientId(10L)).thenReturn(List.of(
                new DatabaseV2TreatmentRepository.TreatmentRecord(100L, 10L, 20L, 30L, 1L, "Acupuncture", "Shoulder tension", LocalDateTime.now())
        ));

        List<DatabaseV2TreatmentController.Response> result = controller.list(10L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).patientId()).isEqualTo(10L);
        assertThat(result.get(0).service()).isEqualTo("Acupuncture");
    }

    @Test
    void createTreatmentSuccess() {
        DatabaseV2TreatmentRepository.TreatmentRecord record = new DatabaseV2TreatmentRepository.TreatmentRecord(
                100L, 10L, 20L, 30L, 1L, "Acupuncture", "Treated tension", LocalDateTime.now()
        );
        when(treatmentRepository.createTreatment(eq(10L), eq(20L), eq(30L), eq(1L), eq("Treated tension"), eq(1L)))
                .thenReturn(record);

        DatabaseV2TreatmentController.Request req = new DatabaseV2TreatmentController.Request(10L, 20L, 30L, 1L, "Treated tension");
        DatabaseV2TreatmentController.Response res = controller.create(req, auth);

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.patientId()).isEqualTo(10L);
        assertThat(res.serviceId()).isEqualTo(1L);
        assertThat(res.service()).isEqualTo("Acupuncture");
        assertThat(res.note()).isEqualTo("Treated tension");
    }

    @Test
    void patientTreatmentHistoryIsBoundedAndIncludesEventTime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 9, 30);
        when(treatmentRepository.patientExists(10L)).thenReturn(true);
        when(treatmentRepository.countByPatientId(10L)).thenReturn(2);
        when(treatmentRepository.findByPatientId(10L, 1, 1)).thenReturn(List.of(
                new DatabaseV2TreatmentRepository.TreatmentRecord(101L, 10L, null, 30L, 1L, "Acupuncture", "Care note", createdAt)
        ));

        var page = controller.history(10L, 1, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).patientId()).isEqualTo(10L);
        assertThat(page.items().get(0).service()).isEqualTo("Acupuncture");
        assertThat(page.items().get(0).createdAt()).isEqualTo(createdAt);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void patientTreatmentHistoryRejectsInvalidPagination() {
        when(treatmentRepository.patientExists(10L)).thenReturn(true);
        assertThatThrownBy(() -> controller.history(10L, -1, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
        assertThatThrownBy(() -> controller.history(10L, 0, 101))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
    }
}
