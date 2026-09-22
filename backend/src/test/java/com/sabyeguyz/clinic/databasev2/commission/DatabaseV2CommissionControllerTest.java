package com.sabyeguyz.clinic.databasev2.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class DatabaseV2CommissionControllerTest {

    private DatabaseV2CommissionRepository repository;
    private CurrentActorProvider actorProvider;
    private DatabaseV2CommissionController commissionController;
    private DatabaseV2CommissionClosingController closingController;
    private DatabaseV2TreatmentFeeRuleController feeRuleController;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        repository = mock(DatabaseV2CommissionRepository.class);
        actorProvider = mock(CurrentActorProvider.class);
        auth = mock(Authentication.class);

        commissionController = new DatabaseV2CommissionController(repository, actorProvider);
        closingController = new DatabaseV2CommissionClosingController(repository, actorProvider);
        feeRuleController = new DatabaseV2TreatmentFeeRuleController(repository, actorProvider);

        when(actorProvider.getActorIdOrDefault(any(), any())).thenReturn(1L);
    }

    @Test
    void listSchemesSuccess() {
        when(repository.listSchemes()).thenReturn(List.of(
                new DatabaseV2CommissionRepository.SchemeRecord(1L, "Scheme 2026", "1", LocalDate.now(), null, true)
        ));

        List<DatabaseV2CommissionController.SchemeResponse> res = commissionController.schemes();
        assertThat(res).hasSize(1);
        assertThat(res.get(0).name()).isEqualTo("Scheme 2026");
        assertThat(res.get(0).active()).isTrue();
    }

    @Test
    void closeMonthSuccess() {
        LocalDate month = LocalDate.of(2026, 9, 1);
        when(repository.closeMonth(eq(month), eq(1L))).thenReturn(List.of(
                new DatabaseV2CommissionRepository.ClosingRecord(
                        10L, month, 5L, "Doctor Strange", new BigDecimal("50000.00"), "1", new BigDecimal("0.08"), "CLOSED"
                )
        ));

        List<DatabaseV2CommissionClosingController.Response> res = closingController.closeMonth(month, auth);
        assertThat(res).hasSize(1);
        assertThat(res.get(0).employeeName()).isEqualTo("Doctor Strange");
        assertThat(res.get(0).sales()).isEqualByComparingTo("50000.00");
        assertThat(res.get(0).status()).isEqualTo("CLOSED");
    }

    @Test
    void createTreatmentFeeRuleSuccess() {
        DatabaseV2CommissionRepository.FeeRuleRecord record = new DatabaseV2CommissionRepository.FeeRuleRecord(
                1L, 5L, "Fixed", new BigDecimal("200.00"), null, LocalDate.now(), null, true
        );
        when(repository.createFeeRule(eq(5L), eq("Fixed"), eq(new BigDecimal("200.00")), any(), any(), any(), eq(1L)))
                .thenReturn(record);

        DatabaseV2TreatmentFeeRuleController.Request req = new DatabaseV2TreatmentFeeRuleController.Request(
                5L, "Fixed", new BigDecimal("200.00"), null, LocalDate.now(), null
        );
        DatabaseV2TreatmentFeeRuleController.Response res = feeRuleController.create(req, auth);
        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.feeValue()).isEqualByComparingTo("200.00");
        assertThat(res.active()).isTrue();
    }
}
