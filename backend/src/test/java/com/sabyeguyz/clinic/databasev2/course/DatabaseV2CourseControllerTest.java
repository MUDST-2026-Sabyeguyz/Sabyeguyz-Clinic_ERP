package com.sabyeguyz.clinic.databasev2.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.course.CourseLedgerResponse;
import com.sabyeguyz.clinic.course.CoursePurchaseRequest;
import com.sabyeguyz.clinic.course.CoursePurchaseResponse;
import com.sabyeguyz.clinic.course.CourseReasonRequest;
import com.sabyeguyz.clinic.course.CourseTransferRequest;
import com.sabyeguyz.clinic.course.CourseTransferResponse;
import com.sabyeguyz.clinic.course.CourseUsageRequest;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.catalog.DatabaseV2ServiceRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.patient.DatabaseV2PatientRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class DatabaseV2CourseControllerTest {

    private DatabaseV2CourseRepository courseRepository;
    private DatabaseV2PatientRepository patientRepository;
    private DatabaseV2BranchRepository branchRepository;
    private DatabaseV2StaffRepository staffRepository;
    private DatabaseV2ServiceRepository serviceRepository;
    private CurrentActorProvider actorProvider;
    private DatabaseV2CourseLedgerController ledgerController;
    private DatabaseV2CourseTransferController transferController;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        courseRepository = mock(DatabaseV2CourseRepository.class);
        patientRepository = mock(DatabaseV2PatientRepository.class);
        branchRepository = mock(DatabaseV2BranchRepository.class);
        staffRepository = mock(DatabaseV2StaffRepository.class);
        serviceRepository = mock(DatabaseV2ServiceRepository.class);
        actorProvider = mock(CurrentActorProvider.class);
        auth = mock(Authentication.class);

        ledgerController = new DatabaseV2CourseLedgerController(
                courseRepository, patientRepository, branchRepository,
                staffRepository, serviceRepository, actorProvider
        );
        transferController = new DatabaseV2CourseTransferController(courseRepository, actorProvider);

        when(actorProvider.getActorIdOrDefault(any(), any())).thenReturn(1L);

        DatabaseV2PatientRepository.PatientRecord patient = mock(DatabaseV2PatientRepository.PatientRecord.class);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));

        DatabaseV2Branch branch = new DatabaseV2Branch(1L, "01", "Main Branch", "123 Road", "021234567", true);
        when(branchRepository.findByCode("01")).thenReturn(Optional.of(branch));
    }

    private DatabaseV2CourseRepository.CoursePurchaseRecord mockCourse(Long id, int remaining, String status) {
        return new DatabaseV2CourseRepository.CoursePurchaseRecord(
                id, "RE-0000001", 1, "Course 10", new BigDecimal("5000.00"),
                10, 0, LocalDate.now().plusDays(365), status,
                30L, "CRS-10", 1L, "RE-0000001",
                10L, "2601090001", "สมชาย ใจดี", 1L, "01",
                1L, 1L,
                10 - remaining, remaining, "Provisional",
                null, null, null, LocalDateTime.now()
        );
    }

    @Test
    void purchaseCourseSuccess() {
        DatabaseV2ServiceRepository.ServiceModel service = new DatabaseV2ServiceRepository.ServiceModel(
                30L, "CRS-10", "Course 10", "Course", new BigDecimal("5000.00"), true, 10, 0, 365
        );
        when(serviceRepository.findById(30L)).thenReturn(Optional.of(service));

        when(courseRepository.purchase(eq(10L), eq(30L), eq(1L), eq("01"), any(), eq(1L)))
                .thenReturn(mockCourse(100L, 10, "Active"));

        CoursePurchaseRequest req = new CoursePurchaseRequest(10L, 30L, "01", null);
        ResponseEntity<CoursePurchaseResponse> res = ledgerController.purchase(req, auth);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getHeaders().getLocation()).hasToString("/api/course-purchases/100");
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().remaining()).isEqualTo(10);
        assertThat(res.getBody().usable()).isTrue();
    }

    @Test
    void useCourseSessionSuccess() {
        when(courseRepository.consumeSession(eq(100L), any(), any(), any(), eq(1L)))
                .thenReturn(mockCourse(100L, 9, "Active"));

        CourseUsageRequest req = new CourseUsageRequest(100L, 200L, "Treated back pain", null);
        ResponseEntity<CoursePurchaseResponse> res = ledgerController.use(req, auth);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getBody().remaining()).isEqualTo(9);
        assertThat(res.getBody().used()).isEqualTo(1);
    }

    @Test
    void reverseUsageSuccess() {
        when(courseRepository.reverseUsage(eq(50L), eq("Mistaken deduction"), eq(1L)))
                .thenReturn(mockCourse(100L, 10, "Active"));

        CourseReasonRequest req = new CourseReasonRequest("Mistaken deduction");
        CoursePurchaseResponse res = ledgerController.reverseUsage(50L, req, auth);

        assertThat(res.remaining()).isEqualTo(10);
        assertThat(res.used()).isEqualTo(0);
    }

    @Test
    void cancelCourseSuccess() {
        when(courseRepository.cancel(eq(100L), eq("Customer requested refund"), eq(1L)))
                .thenReturn(mockCourse(100L, 0, "Cancelled"));

        CourseReasonRequest req = new CourseReasonRequest("Customer requested refund");
        CoursePurchaseResponse res = ledgerController.cancel(100L, req, auth);

        assertThat(res.remaining()).isEqualTo(0);
        assertThat(res.usable()).isFalse();
    }

    @Test
    void transferCourseSuccess() {
        DatabaseV2CourseRepository.CourseTransferRecord record = new DatabaseV2CourseRepository.CourseTransferRecord(
                1L, 100L, 101L, 10L, "2601090001", "สมชาย ใจดี",
                20L, "2601090002", "สมหญิง รักดี", 30L, "01",
                3, 1L, "Gift to sister", LocalDateTime.now()
        );
        when(courseRepository.transfer(eq(100L), eq(20L), eq(3), eq("Gift to sister"), eq(1L)))
                .thenReturn(record);

        CourseTransferRequest req = new CourseTransferRequest(100L, 20L, 3, "Gift to sister");
        ResponseEntity<CourseTransferResponse> res = transferController.transfer(req, auth);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getBody().quantity()).isEqualTo(3);
        assertThat(res.getBody().sourcePurchaseId()).isEqualTo(100L);
        assertThat(res.getBody().destinationPurchaseId()).isEqualTo(101L);
    }

    @Test
    void patientCourseHistoryIsBoundedAndReturnsTotals() {
        when(courseRepository.countByPatientId(10L)).thenReturn(11);
        when(courseRepository.findByPatientId(10L, 10, 0)).thenReturn(List.of(mockCourse(100L, 3, "Active")));

        var page = ledgerController.historyByPatient(10L, 0, 10);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(100L);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalItems()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(2);
        verify(courseRepository).findByPatientId(10L, 10, 0);
    }

    @Test
    void patientCourseHistoryRejectsInvalidPagination() {
        assertThatThrownBy(() -> ledgerController.historyByPatient(10L, -1, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
        assertThatThrownBy(() -> ledgerController.historyByPatient(10L, 0, 101))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
    }
}
