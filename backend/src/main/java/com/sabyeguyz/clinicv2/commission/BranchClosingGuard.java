package com.sabyeguyz.clinicv2.commission;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;

@Service
public class BranchClosingGuard {
    private final MonthlyCommissionClosingRepository closings;
    private final CourseRepository courses;
    private final BranchMonthlyClosingRepository branchClosings;

    public BranchClosingGuard(
            MonthlyCommissionClosingRepository closings,
            CourseRepository courses,
            BranchMonthlyClosingRepository branchClosings) {
        this.closings = closings;
        this.courses = courses;
        this.branchClosings = branchClosings;

    }

    /** Requires the selected branch's current commission month to be closed. */
    @Transactional
    public void requireCurrentMonthClosed(Branch branch) {
        if (branch == null || branch.getId() == null) {
            throw new IllegalArgumentException("ต้องเลือกสาขา");
        }

        String month = YearMonth.from(ClinicTime.today()).toString();

        closings.lockBranchMonth(branch.getId(), month);

        BranchMonthlyClosing branchClosing = branchClosings.findByBranchIdAndClosingMonth(branch.getId(), month)
                        .orElseThrow(() -> new IllegalStateException("ยังไม่ได้ยืนยันปิดรอบสาขาเดือน " + month));

        if (!branchClosing.isClosed()) {
            throw new IllegalStateException(
                    "รอบสาขาเดือน " + month + " ยังเปิดอยู่");
        }

        List<MonthlyCommissionClosing> rows =
                closings.findByClosingMonthAndBranchId(
                        month, branch.getId());

        if (rows.stream().anyMatch(row -> !row.isClosed())) {
            throw new IllegalStateException(
                    "สาขานี้ยังปิดยอดเดือน " + month + " ไม่ครบ");
        }

        Set<Long> closedStaffIds = new HashSet<>();
        rows.forEach(row -> closedStaffIds.add(row.getStaff().getId()));

        List<Long> sellerIds =
                courses.findDistinctCreditedStaffIdsBySaleMonthAndBranchId(
                        month, branch.getId());

        if (!closedStaffIds.containsAll(sellerIds)) {
            throw new IllegalStateException(
                    "ยังมีผู้ขายของสาขานี้ที่ไม่ได้ปิดยอดเดือน " + month);
        }
    }
}
