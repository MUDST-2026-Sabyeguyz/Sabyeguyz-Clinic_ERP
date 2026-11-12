package com.sabyeguyz.clinicv2.commission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.enums.AdjustmentStatus;
import com.sabyeguyz.clinicv2.enums.ApplicationStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Boundary checks for the current, explicitly approved adjustment application workflow. */
class AutomaticAdjustmentCheckTest {
    private static void id(Object target, long value) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void settlesAppliedDeductionAndRoutesResignedBalanceForExternalReview() throws Exception {
        Staff person = new Staff();
        id(person, 7);
        person.setTerminationDate(LocalDate.of(2026, 11, 1));

        StaffPayAdjustment adjustment = new StaffPayAdjustment();
        id(adjustment, 11);
        adjustment.setStaff(person);
        adjustment.setAmount(new BigDecimal("-3000.00"));
        adjustment.setRemainingAmount(new BigDecimal("3000.00"));
        adjustment.setAdjustmentStatus(AdjustmentStatus.Approved);

        StaffMonthlyPayment payment = new StaffMonthlyPayment();
        id(payment, 21);
        AdjustmentApplication applied = new AdjustmentApplication();
        id(applied, 31);
        applied.setAdjustment(adjustment);
        applied.setMonthlyPayment(payment);
        applied.setAmount(new BigDecimal("-2000.00"));
        applied.setApplicationStatus(ApplicationStatus.Applied);

        AdjustmentApplication draft = new AdjustmentApplication();
        id(draft, 32);
        draft.setAdjustment(adjustment);
        draft.setMonthlyPayment(payment);
        draft.setAmount(new BigDecimal("-1000.00"));
        draft.setApplicationStatus(ApplicationStatus.Draft);

        List<AdjustmentApplication> applications = new java.util.ArrayList<>(List.of(applied, draft));
        var applicationStub = new V2RepositoryStub<>(AdjustmentApplicationRepository.class);
        applicationStub.on("findByAdjustmentIdOrderByIdAsc", ignored -> List.copyOf(applications));
        applicationStub.on("findByMonthlyPaymentIdOrderByIdAsc", ignored -> List.of(applied));
        var adjustmentStub = new V2RepositoryStub<>(StaffPayAdjustmentRepository.class);
        adjustmentStub.on("findById", ignored -> Optional.of(adjustment));

        StaffMonthlyPaymentService paymentService = new StaffMonthlyPaymentService(
                null, null, null, null, applicationStub.proxy(), adjustmentStub.proxy(), null, null, null);
        paymentService.settle(adjustment);
        assertEquals(new BigDecimal("2000.00"), adjustment.getProcessedAmount());
        assertEquals(new BigDecimal("1000.00"), adjustment.getRemainingAmount());
        assertEquals(AdjustmentStatus.PartiallyApplied, adjustment.getAdjustmentStatus());

        StaffPayAdjustmentService adjustmentService = new StaffPayAdjustmentService(
                adjustmentStub.proxy(), applicationStub.proxy(), null, paymentService,
                null, null, null, null, null, null);
        adjustmentService.externalReview(adjustment.getId());
        assertEquals(ApplicationStatus.Cancelled, draft.getApplicationStatus());
        assertEquals(AdjustmentStatus.ExternalReview, adjustment.getAdjustmentStatus());
        assertEquals(new BigDecimal("1000.00"), adjustment.getRemainingAmount());
    }
}
