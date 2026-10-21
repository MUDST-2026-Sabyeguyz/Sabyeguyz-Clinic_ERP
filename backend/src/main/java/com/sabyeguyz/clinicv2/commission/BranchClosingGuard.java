package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchClosingGuard {
    private final MonthlyCommissionClosingRepository closings;
    private final CourseRepository courses;

    public BranchClosingGuard(
            MonthlyCommissionClosingRepository closings,
            CourseRepository courses) {
        this.closings = closings;
        this.courses = courses;
    }

    /** Requires the selected branch's current commission month to be closed. */
    @Transactional
    public void requireCurrentMonthClosed(Branch branch) {
        if (branch == null || branch.getId() == null) {
            throw new IllegalArgumentException("ต้องเลือกสาขา");
        }

        String month = YearMonth.from(ClinicTime.today()).toString();

        closings.lockBranchMonth(branch.getId(), month);

        List<MonthlyCommissionClosing> rows =
                closings.findByClosingMonthAndBranchId(
                        month, branch.getId());

        if (rows.isEmpty()
                || rows.stream().anyMatch(row -> !row.isClosed())) {
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
