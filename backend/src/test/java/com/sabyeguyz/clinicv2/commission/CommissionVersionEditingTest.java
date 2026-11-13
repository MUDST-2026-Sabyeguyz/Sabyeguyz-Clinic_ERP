package com.sabyeguyz.clinicv2.commission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.FeeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** US072.07/073.06 initial setup and US075.02/.05/.06 future-version boundaries. */
class CommissionVersionEditingTest {
    private final CommissionSchemeRepository schemes = mock(CommissionSchemeRepository.class);
    private final CommissionTierRepository tiers = mock(CommissionTierRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final MonthlyCommissionClosingRepository closings = mock(MonthlyCommissionClosingRepository.class);
    private final TreatmentFeeRuleRepository rules = mock(TreatmentFeeRuleRepository.class);
    private final BranchClosingGuard guard = mock(BranchClosingGuard.class);
    private final Branch branch = branch();
    private final LocalDate next = YearMonth.from(ClinicTime.today()).plusMonths(1).atDay(1);

    private static Branch branch() {
        Branch branch = new Branch();
        ReflectionTestUtils.setField(branch, "id", 1L);
        return branch;
    }
    private CommissionSchemeService schemeService() {
        when(schemes.save(any())).thenAnswer(call -> call.getArgument(0));
        return new CommissionSchemeService(schemes, tiers, courses, closings, guard);
    }
    private TreatmentFeeRuleService feeService() {
        when(rules.save(any())).thenAnswer(call -> call.getArgument(0));
        return new TreatmentFeeRuleService(rules, guard);
    }
    private CommissionScheme scheme(LocalDate start) {
        CommissionScheme scheme = new CommissionScheme();
        ReflectionTestUtils.setField(scheme, "id", 2L);
        scheme.setSchemeName("Version under test");
        scheme.setSchemeVersion(2);
        scheme.setEffectiveFrom(start);
        when(schemes.findById(2L)).thenReturn(Optional.of(scheme));
        when(schemes.findByOrderByEffectiveFromDesc()).thenReturn(List.of(scheme));
        return scheme;
    }
    private TreatmentFeeRule fee(LocalDate start) {
        TreatmentFeeRule rule = new TreatmentFeeRule();
        ReflectionTestUtils.setField(rule, "id", 2L);
        rule.setRuleVersion(2);
        rule.setFeeType(FeeType.Fixed);
        rule.setFeeValue(new BigDecimal("45.00"));
        rule.setEffectiveFrom(start);
        when(rules.findById(2L)).thenReturn(Optional.of(rule));
        when(rules.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(rule));
        return rule;
    }
    private static List<CommissionSchemeService.TierInput> tier(String rate) {
        return List.of(new CommissionSchemeService.TierInput(BigDecimal.ZERO, null, new BigDecimal(rate), 1));
    }
    private void closedMonthUnavailable() {
        doThrow(new IllegalStateException("Branch month has reopened")).when(guard).requireCurrentMonthClosed(any());
    }

    @Test
    void pendingCommissionVersionCanBeEditedWithoutReclosingBranch() {
        closedMonthUnavailable();
        CommissionScheme scheme = scheme(next);
        var result = schemeService().update(2L, "Revised future", null, null, branch, tier("0.07"), 1L);
        assertSame(scheme, result);
        assertEquals(2, result.getSchemeVersion());
        assertEquals(next, result.getEffectiveFrom());
        verify(guard, never()).requireCurrentMonthClosed(any());
        verify(tiers).save(argThat(row -> new BigDecimal("0.07").compareTo(row.getCommissionRate()) == 0));
    }

    @Test
    void pendingFeeVersionCanBeEditedWithoutReclosingBranch() {
        closedMonthUnavailable();
        TreatmentFeeRule rule = fee(next);
        var result = feeService().update(2L, FeeType.Percentage, new BigDecimal("3"), null, null, branch, 1L);
        assertSame(rule, result);
        assertEquals(2, result.getRuleVersion());
        assertEquals(next, result.getEffectiveFrom());
        assertEquals(FeeType.Percentage, result.getFeeType());
        verify(guard, never()).requireCurrentMonthClosed(any());
    }

    @Test
    void startedButUnreferencedCommissionCannotHaveItsRatesOverwritten() {
        var scheme = scheme(YearMonth.from(ClinicTime.today()).atDay(1));
        assertThrows(IllegalStateException.class,
                () -> schemeService().update(2L, null, null, null, branch, tier("0.12"), 1L));
        assertEquals(2, scheme.getSchemeVersion());
        verify(tiers, never()).deleteAll(anyIterable());
        verify(schemes, never()).save(any());
    }

    @Test
    void firstCommissionSetupDoesNotNeedAnExistingClosedMonth() {
        closedMonthUnavailable();
        when(schemes.findByOrderByEffectiveFromDesc()).thenReturn(List.of());
        var result = schemeService().create("Initial", YearMonth.from(ClinicTime.today()).atDay(1), null,
                branch, null, tier("0.05"), 1L);
        assertEquals(1, result.getSchemeVersion());
        verify(guard, never()).requireCurrentMonthClosed(any());
    }

    @Test
    void firstFeeSetupDoesNotNeedAnExistingClosedMonth() {
        closedMonthUnavailable();
        when(rules.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of());
        when(rules.findMaxRuleVersion()).thenReturn(0);
        var result = feeService().create(FeeType.Fixed, new BigDecimal("45"),
                YearMonth.from(ClinicTime.today()).atDay(1), null, branch, null, 1L);
        assertEquals(1, result.getRuleVersion());
        verify(guard, never()).requireCurrentMonthClosed(any());
    }

    @Test
    void editingFutureFeeDoesNotSilentlyNormalizeInvalidMidMonthStart() {
        var rule = fee(next);
        assertThrows(IllegalArgumentException.class, () -> feeService().update(2L, null, null,
                next.plusDays(14), null, branch, 1L));
        assertEquals(next, rule.getEffectiveFrom());
        verify(rules, never()).save(any());
    }

    @Test
    void pendingEditsStillRequireAnExplicitBranch() {
        scheme(next);
        assertThrows(IllegalArgumentException.class,
                () -> schemeService().update(2L, "x", null, null, null, tier("0.05"), 1L));
        fee(next);
        assertThrows(IllegalArgumentException.class,
                () -> feeService().update(2L, null, new BigDecimal("50"), null, null, null, 1L));
    }

    @Test
    void invalidTierRowsAreRejectedBeforeTheSchemeIsSaved() {
        var hasGap = List.of(
                new CommissionSchemeService.TierInput(BigDecimal.ZERO, new BigDecimal("50000.00"),
                        new BigDecimal("0.05"), 1),
                new CommissionSchemeService.TierInput(new BigDecimal("50000.02"), null,
                        new BigDecimal("0.07"), 2));
        assertThrows(IllegalArgumentException.class, () -> schemeService().create(
                "Invalid tiers", next, null, branch, null, hasGap, 1L));
        verify(schemes, never()).save(any());
        verify(tiers, never()).save(any());
    }

    @Test
    void invalidCentralFeeValuesAreRejectedBeforeTheRuleIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> feeService().create(
                FeeType.Percentage, new BigDecimal("100.01"), next, null, branch, null, 1L));
        assertThrows(IllegalArgumentException.class, () -> feeService().create(
                FeeType.Fixed, new BigDecimal("12.345"), next, null, branch, null, 1L));
        verify(rules, never()).save(any());
    }
}
