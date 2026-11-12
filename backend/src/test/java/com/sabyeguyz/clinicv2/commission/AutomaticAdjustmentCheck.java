package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.AdjustmentStatus;
import com.sabyeguyz.clinicv2.enums.ApplicationStatus;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Standalone boundary check for automatic, single-entry adjustment carry-over. */
public class AutomaticAdjustmentCheck {
    @Test
    void automaticAdjustmentsCarryOverWithinTheBranch() throws Exception {
        main(new String[0]);
    }

    private static void id(Object target, long value) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Staff person = new Staff();
        id(person, 7);
        person.setStaffStatus(true);
        Branch branch = new Branch();
        id(branch, 2);
        StaffPayAdjustment adjustment = new StaffPayAdjustment();
        id(adjustment, 11);
        adjustment.setStaff(person);
        adjustment.setBranch(branch);
        adjustment.setAutomatic(true);
        adjustment.setAmount(new BigDecimal("-3000.00"));
        adjustment.setRemainingAmount(new BigDecimal("3000.00"));
        adjustment.setAppliedMonth("2026-11");
        adjustment.setAdjustmentStatus(AdjustmentStatus.Approved);
        adjustment.setApprovalReference("AGREEMENT-1");
        adjustment.setApprovedBy(3L);
        adjustment.setCreatedBy(3L);

        var adjustmentStub = new V2RepositoryStub<>(StaffPayAdjustmentRepository.class);
        adjustmentStub.on("findByStaffIdAndAutomaticTrueAndBranchIdAndAppliedMonthLessThanEqualOrderByIdAsc",
                ignored -> List.of(adjustment));
        var applicationStub = new V2RepositoryStub<>(AdjustmentApplicationRepository.class);
        var paymentStub = new V2RepositoryStub<>(StaffMonthlyPaymentRepository.class);
        List<StaffMonthlyPayment> payments = new ArrayList<>();
        paymentStub.on("findByStaffIdOrderByPaymentMonthDesc", ignored -> List.copyOf(payments));
        List<AdjustmentApplication> saved = new ArrayList<>();
        applicationStub.on("findByAdjustmentIdOrderByIdAsc", ignored -> List.copyOf(saved));
        applicationStub.on("findByMonthlyPaymentIdOrderByIdAsc", call -> saved.stream()
                .filter(row -> row.getMonthlyPayment().getId().equals(call[0])).toList());
        applicationStub.on("save", call -> {
            AdjustmentApplication row = (AdjustmentApplication) call[0];
            if (!saved.contains(row)) saved.add(row);
            return row;
        });
        boolean[] refreshed = { false };
        var service = new StaffMonthlyPaymentService(paymentStub.proxy(), null, null, null,
                applicationStub.proxy(), adjustmentStub.proxy(), null, null, null) {
            @Override
            public StaffMonthlyPayment refresh(StaffMonthlyPayment payment) {
                refreshed[0] = true;
                return payment;
            }
        };
        StaffMonthlyPayment first = new StaffMonthlyPayment();
        id(first, 21);
        first.setStaff(person);
        first.setBranch(branch);
        first.setPaymentMonth("2026-11");
        first.setPaymentStatus(PaymentStatus.Open);
        payments.add(first);
        service.scheduleAutomaticAdjustments(first);
        check(saved.size() == 1, "first month should schedule once");
        check(saved.get(0).getAmount().compareTo(new BigDecimal("-3000.00")) == 0,
                "first month should reserve full remaining amount");
        check("AGREEMENT-1".equals(saved.get(0).getApprovalReference()), "consent reference must travel with deduction");
        service.scheduleAutomaticAdjustments(first);
        check(saved.size() == 1, "refresh must not duplicate a draft");

        check(service.limitDeductions(first, new BigDecimal("2000.00"), true)
                .compareTo(new BigDecimal("-2000.00")) == 0,
                "deduction must be capped at actual earnings");
        check(saved.get(0).getAmount().compareTo(new BigDecimal("-2000.00")) == 0,
                "actual application must record only the amount paid");
        saved.get(0).setApplicationStatus(ApplicationStatus.Applied);
        service.settle(adjustment);
        check(adjustment.getRemainingAmount().compareTo(new BigDecimal("1000.00")) == 0,
                "unpaid balance must remain for the next month");
        check(adjustment.getAdjustmentStatus() == AdjustmentStatus.PartiallyApplied,
                "partially applied adjustment must keep its state");
        StaffMonthlyPayment next = new StaffMonthlyPayment();
        id(next, 22);
        next.setStaff(person);
        next.setBranch(branch);
        next.setPaymentMonth("2026-12");
        next.setPaymentStatus(PaymentStatus.Open);
        payments.add(next);
        service.scheduleAutomaticAdjustments(next);
        check(saved.size() == 2, "remaining balance should reach the next month");
        check(saved.get(1).getAmount().compareTo(new BigDecimal("-1000.00")) == 0,
                "next month must use only the remaining balance");

        adjustmentStub.on("findByStaffIdOrderByIdDesc", ignored -> List.of(adjustment));
        var adjustmentService = new StaffPayAdjustmentService(adjustmentStub.proxy(),
                applicationStub.proxy(), paymentStub.proxy(), service, null, null, null, null, null, null);
        adjustmentService.cancelOnTermination(person.getId(), 3L);
        check(saved.get(1).getApplicationStatus() == ApplicationStatus.Cancelled,
                "resignation must cancel an unpaid application");
        check(adjustment.getAdjustmentStatus() == AdjustmentStatus.Cancelled
                        && "นักกายภาพลาออก".equals(adjustment.getCancellationReason()),
                "resignation must label the remaining balance");
        check(refreshed[0], "open payment must be recalculated after cancellation");
        service.scheduleAutomaticAdjustments(next);
        check(saved.size() == 2, "cancelled adjustment must not be scheduled again");
        System.out.println("AUTOMATIC_ADJUSTMENT_OK");
    }
}
