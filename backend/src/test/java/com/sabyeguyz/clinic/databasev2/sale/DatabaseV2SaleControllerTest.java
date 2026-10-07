package com.sabyeguyz.clinic.databasev2.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.sabyeguyz.clinic.databasev2.payment.DatabaseV2PaymentMethodRepository;
import com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import com.sabyeguyz.clinic.sale.SaleItemType;
import com.sabyeguyz.clinic.sale.SaleRequest;
import com.sabyeguyz.clinic.sale.SaleResponse;
import com.sabyeguyz.clinic.sale.SaleSearchResponse;
import com.sabyeguyz.clinic.sale.SaleStatus;
import com.sabyeguyz.clinic.sale.SaleVoidRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class DatabaseV2SaleControllerTest {

    private DatabaseV2SaleRepository saleRepository;
    private DatabaseV2PatientRepository patientRepository;
    private DatabaseV2BranchRepository branchRepository;
    private DatabaseV2StaffRepository staffRepository;
    private DatabaseV2ServiceRepository serviceRepository;
    private DatabaseV2PaymentMethodRepository paymentMethodRepository;
    private CurrentActorProvider actorProvider;
    private BranchAccessGuard branchAccess;
    private DatabaseV2SaleController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        saleRepository = mock(DatabaseV2SaleRepository.class);
        patientRepository = mock(DatabaseV2PatientRepository.class);
        branchRepository = mock(DatabaseV2BranchRepository.class);
        staffRepository = mock(DatabaseV2StaffRepository.class);
        serviceRepository = mock(DatabaseV2ServiceRepository.class);
        paymentMethodRepository = mock(DatabaseV2PaymentMethodRepository.class);
        actorProvider = mock(CurrentActorProvider.class);
        branchAccess = mock(BranchAccessGuard.class);
        auth = mock(Authentication.class);

        controller = new DatabaseV2SaleController(
                saleRepository, patientRepository, branchRepository,
                staffRepository, serviceRepository, paymentMethodRepository,
                actorProvider, branchAccess
        );

        when(actorProvider.getActorIdOrDefault(any(), any())).thenReturn(1L);
        when(branchAccess.actorId(auth)).thenReturn(1L);

        DatabaseV2PatientRepository.PatientRecord patient = mock(DatabaseV2PatientRepository.PatientRecord.class);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));

        DatabaseV2Branch branch = new DatabaseV2Branch(1L, "01", "Branch 01", "123 Road", "021234567", true);
        when(branchRepository.findByCode("01")).thenReturn(Optional.of(branch));

        DatabaseV2PaymentMethodRepository.PaymentMethodRecord cash =
                new DatabaseV2PaymentMethodRepository.PaymentMethodRecord(1L, "CASH", "CASH", true);
        when(paymentMethodRepository.findByName("CASH")).thenReturn(Optional.of(cash));
    }

    private DatabaseV2SaleRepository.SaleRecord mockRecord(Long id, String status) {
        return new DatabaseV2SaleRepository.SaleRecord(
                id, "RE-0000001", 10L, "2601090001", "สมชาย ใจดี", "01",
                null, null, null,
                new BigDecimal("5000.00"), "VOIDED".equals(status) ? BigDecimal.ZERO : new BigDecimal("5000.00"),
                status, null, LocalDateTime.now(), "VOIDED".equals(status) ? LocalDateTime.now() : null,
                "VOIDED".equals(status) ? "Reason" : null,
                List.of(new DatabaseV2SaleRepository.SaleItemRecord(
                        100L, "COURSE", "Course 10 visits", new BigDecimal("5000.00"), 1, new BigDecimal("5000.00"), 50L
                )),
                List.of(new DatabaseV2SaleRepository.SalePaymentRecord(
                        200L, "PAYMENT", "CASH", new BigDecimal("5000.00"), null, null, LocalDateTime.now()
                ))
        );
    }

    @Test
    void createSaleSuccess() {
        DatabaseV2ServiceRepository.ServiceModel courseService = new DatabaseV2ServiceRepository.ServiceModel(
                30L, "CRS-01", "Course 10 visits", "Course", new BigDecimal("5000.00"), true, 10, 0, 365
        );
        when(serviceRepository.findById(30L)).thenReturn(Optional.of(courseService));

        when(saleRepository.allocateReceipt()).thenReturn(new DatabaseV2SaleRepository.GeneratedReceipt(1L, "RE-0000001"));
        when(saleRepository.createSalesTransaction(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(100L);
        when(saleRepository.createCourse(any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(50L);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(mockRecord(1L, "PAID")));

        SaleRequest request = new SaleRequest(
                10L, "01", null, "First purchase",
                List.of(new SaleRequest.Item(SaleItemType.COURSE, 30L, 1)),
                List.of(new SaleRequest.Payment("CASH", new BigDecimal("5000.00")))
        );

        ResponseEntity<SaleResponse> response = controller.create(request, auth);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/receipts/1");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().receiptNo()).isEqualTo("RE-0000001");
        assertThat(response.getBody().total()).isEqualByComparingTo("5000.00");
        assertThat(response.getBody().paid()).isEqualByComparingTo("5000.00");
        assertThat(response.getBody().status()).isEqualTo(SaleStatus.PAID);
        verify(branchAccess).require(auth, 1L);
    }

    @Test
    void createSaleRejectsBranchOutsideAuthenticatedAccessBeforeAllocatingReceipt() {
        DatabaseV2Branch otherBranch = new DatabaseV2Branch(2L, "02", "Branch 02", "123 Road", "021234568", true);
        when(branchRepository.findByCode("02")).thenReturn(Optional.of(otherBranch));
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("no branch access"))
                .when(branchAccess).require(auth, 2L);
        SaleRequest request = new SaleRequest(10L, "02", null, null,
                List.of(new SaleRequest.Item(SaleItemType.SINGLE_VISIT, 31L, 1)),
                List.of(new SaleRequest.Payment("CASH", new BigDecimal("1200.00"))));

        assertThatThrownBy(() -> controller.create(request, auth))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(saleRepository, org.mockito.Mockito.never()).allocateReceipt();
    }

    @Test
    void createSaleRejectsMismatchPayments() {
        DatabaseV2ServiceRepository.ServiceModel singleVisit = new DatabaseV2ServiceRepository.ServiceModel(
                31L, "SRV-01", "Single Visit", "SingleVisit", new BigDecimal("1200.00"), true, null, null, null
        );
        when(serviceRepository.findById(31L)).thenReturn(Optional.of(singleVisit));

        SaleRequest request = new SaleRequest(
                10L, "01", null, null,
                List.of(new SaleRequest.Item(SaleItemType.SINGLE_VISIT, 31L, 1)),
                List.of(new SaleRequest.Payment("CASH", new BigDecimal("1000.00"))) // 1000 instead of 1200
        );

        assertThatThrownBy(() -> controller.create(request, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่ตรงกับยอดที่ต้องชำระ");
    }

    @Test
    void reverseSaleSuccess() {
        when(saleRepository.findById(1L)).thenReturn(
                Optional.of(mockRecord(1L, "PAID")),
                Optional.of(mockRecord(1L, "VOIDED"))
        );
        when(saleRepository.isVoided(1L)).thenReturn(false);

        SaleVoidRequest req = new SaleVoidRequest("Customer requested refund");

        SaleResponse res = controller.reverse(1L, req, auth);

        verify(saleRepository).voidReceipt(eq(1L), eq("Customer requested refund"), eq(1L));
        assertThat(res.status()).isEqualTo(SaleStatus.VOIDED);
        assertThat(res.paid()).isEqualByComparingTo("0.00");
    }

    @Test
    void combinedDiscountsAreRejectedBeforeAllocatingReceipt() {
        var service = new DatabaseV2ServiceRepository.ServiceModel(
                31L, "SRV-01", "Single Visit", "SingleVisit", new BigDecimal("2000.00"), true, null, null, null);
        when(serviceRepository.findById(31L)).thenReturn(Optional.of(service));
        var request = new SaleRequest(10L, "01", null, null,
                List.of(new SaleRequest.Item(SaleItemType.SINGLE_VISIT, 31L, 1, new BigDecimal("100.00"))),
                List.of(new SaleRequest.Payment("CASH", new BigDecimal("1800.00"))), new BigDecimal("100.00"));

        assertThatThrownBy(() -> controller.create(request, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ส่วนลดรายการร่วมกับส่วนลดท้ายบิล");
        org.mockito.Mockito.verifyNoInteractions(saleRepository);
    }

    @Test
    void reverseSaleRejectsIfAlreadyVoided() {
        when(saleRepository.findById(1L)).thenReturn(Optional.of(mockRecord(1L, "VOIDED")));
        when(saleRepository.isVoided(1L)).thenReturn(true);

        SaleVoidRequest req = new SaleVoidRequest("Again");

        assertThatThrownBy(() -> controller.reverse(1L, req, auth))
                .isInstanceOf(DatabaseV2ConflictException.class)
                .hasMessageContaining("ใบเสร็จนี้ถูกยกเลิกไปแล้ว");
    }

    @Test
    void listSalesReturnsPage() {
        DatabaseV2SaleRepository.SaleSummaryRecord r = new DatabaseV2SaleRepository.SaleSummaryRecord(
                1L, "RE-0000001", 10L, "2601090001", "สมชาย ใจดี", "01",
                null, null, null, new BigDecimal("5000.00"), new BigDecimal("5000.00"),
                "PAID", LocalDateTime.now(), null
        );

        when(saleRepository.searchAccessible(eq(10L), eq("01"), eq(20), eq(0), eq(1L))).thenReturn(List.of(r));
        when(saleRepository.countSearchAccessible(eq(10L), eq("01"), eq(1L))).thenReturn(1);

        SaleSearchResponse page = controller.list(10L, "01", 0, 20, auth);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).receiptNo()).isEqualTo("RE-0000001");
        verify(branchAccess).require(auth, 1L);
    }

    @Test
    void listSalesRejectsPageWhenOffsetOverflows() {
        assertThatThrownBy(() -> controller.list(10L, "01", Integer.MAX_VALUE, 100, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page เกินขอบเขต");

        org.mockito.Mockito.verifyNoInteractions(saleRepository);
    }
}
