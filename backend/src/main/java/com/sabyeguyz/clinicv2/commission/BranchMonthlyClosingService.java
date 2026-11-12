package com.sabyeguyz.clinicv2.commission;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.BranchClosingStatus;

@Service
public class BranchMonthlyClosingService {
    private final BranchMonthlyClosingRepository branchClosings;
    private final MonthlyCommissionClosingRepository closings;
    private final CourseRepository courses;

    public BranchMonthlyClosingService(
            BranchMonthlyClosingRepository branchClosings,
            MonthlyCommissionClosingRepository closings,
            CourseRepository courses) {
        this.branchClosings = branchClosings;
        this.closings = closings;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public Optional<BranchMonthlyClosing> find(
            Branch branch,
            String month) {
        requireBranchAndMonth(branch, month);

        return branchClosings.findByBranchIdAndClosingMonth(
                branch.getId(), month);
    }

    /** Confirms the branch only after all individual closings are complete. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BranchMonthlyClosing confirmClosed(
            Branch branch,
            String month,
            Long actorId) {
        requireBranchAndMonth(branch, month);

        if (actorId == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ปิดรอบ");
        }

        closings.lockBranchMonth(branch.getId(), month);

        var rows = closings.findByClosingMonthAndBranchId(
                month, branch.getId());

        if (rows.stream().anyMatch(row -> !row.isClosed())) {
            throw new IllegalStateException(
                    "ยังมีรายละเอียดรายคนที่ไม่ได้ปิดยอด");
        }

        Set<Long> closedStaffIds = new HashSet<>();
        rows.forEach(row ->
                closedStaffIds.add(row.getStaff().getId()));

        var sellerIds =
                courses.findDistinctCreditedStaffIdsBySaleMonthAndBranchId(
                        month, branch.getId());

        if (!closedStaffIds.containsAll(sellerIds)) {
            throw new IllegalStateException(
                    "ยังมีผู้ขายที่ไม่ได้ปิดยอดเดือนนี้");
        }

        BranchMonthlyClosing closing =
                branchClosings.findByBranchIdAndClosingMonth(branch.getId(), month).orElseGet(() -> {
                            BranchMonthlyClosing created =
                                    new BranchMonthlyClosing();
                            created.setBranch(branch);
                            created.setClosingMonth(month);

                            return created;
                        });

        if (closing.isClosed()) {
            return closing;
        }

        closing.setClosingStatus(BranchClosingStatus.Closed);
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(actorId);

        return branchClosings.save(closing);
    }

    /**
     * Marks the branch Open after an individual closing was reopened successfully.
     * Returns true only when the branch state changed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markOpenAfterIndividualReopen(
            Branch branch,
            String month) {
        requireBranchAndMonth(branch, month);

        closings.lockBranchMonth(branch.getId(), month);

        var existing = branchClosings.findByBranchIdAndClosingMonth(
                branch.getId(), month);

        // Historical rounds without a header are not treated as Closed.
        if (existing.isEmpty() || !existing.get().isClosed()) {
            return false;
        }

        BranchMonthlyClosing closing = existing.get();
        closing.setClosingStatus(BranchClosingStatus.Open);
        closing.setClosedAt(null);
        closing.setClosedBy(null);

        branchClosings.save(closing);

        return true;
    }

    /** Reopens only a branch round without individual closings or course sales. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BranchMonthlyClosing reopenEmpty(
            Branch branch,
            String month,
            String reason) {
        requireBranchAndMonth(branch, month);

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("ต้องระบุเหตุผลที่ Reopen");
        }

        if (reason.trim().length() > 255) {
            throw new IllegalArgumentException(
                    "เหตุผลที่ Reopen ยาวเกิน 255 ตัวอักษร");
        }

        closings.lockBranchMonth(branch.getId(), month);

        if (!closings.findByClosingMonthAndBranchId(
                month, branch.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "รอบนี้มีรายละเอียดรายคน ให้ Reopen ผ่านรายการรายคน");
        }

        if (!courses.findDistinctCreditedStaffIdsBySaleMonthAndBranchId(
                month, branch.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "รอบนี้มีรายการขายคอร์ส ไม่ใช่รอบว่าง");
        }

        BranchMonthlyClosing closing =
                branchClosings.findByBranchIdAndClosingMonth(branch.getId(), month)
                        .orElseThrow(() -> new IllegalStateException(
                                "ยังไม่มีการยืนยันปิดรอบสาขานี้"));

        if (!closing.isClosed()) {
            throw new IllegalStateException("รอบสาขานี้ยังไม่ได้ปิด");
        }

        closing.setClosingStatus(BranchClosingStatus.Open);
        closing.setClosedAt(null);
        closing.setClosedBy(null);

        return branchClosings.save(closing);
    }

    /** Prevents new course sales from entering a closed branch or seller round. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCourseSaleOpen(
            Branch branch,
            String month,
            Long sellerId) {
        requireBranchAndMonth(branch, month);

        if (sellerId == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ขายคอร์ส");
        }

        closings.lockBranchMonth(branch.getId(), month);

        boolean branchClosed = branchClosings.findByBranchIdAndClosingMonth(branch.getId(), month)
                        .map(BranchMonthlyClosing::isClosed)
                        .orElse(false);

        if (branchClosed) {
            throw new IllegalStateException(
                    "รอบสาขาเดือน " + month
                            + " ปิดแล้ว ต้อง Reopen ก่อนขายคอร์สเพิ่ม");
        }

        boolean sellerClosed = closings.findByClosingMonthAndStaffIdAndBranchId(month, sellerId, branch.getId())
                        .map(MonthlyCommissionClosing::isClosed)
                        .orElse(false);

        if (sellerClosed) {
            throw new IllegalStateException("รอบผู้ขายเดือน " + month + " ปิดหรือ Finalized แล้ว ต้องตรวจสิทธิ์ Reopen ก่อนขายคอร์สเพิ่ม");
        }
    }

    private static void requireBranchAndMonth(
            Branch branch,
            String month) {
        if (branch == null || branch.getId() == null) {
            throw new IllegalArgumentException("ต้องระบุสาขา");
        }

        StaffMonthlyPaymentService.requireMonth(month);
    }

}
