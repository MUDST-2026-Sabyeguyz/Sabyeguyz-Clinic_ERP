package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.ClosingStatus;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.branch.Branch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly Closing (Commission_System_Requirement_Dev §10-12, §24, §30).
 * Freezes the seller's Commission Rate for the month and stamps it onto
 * every Course they sold that month — the "Course must remember its own
 * Tier forever" rule (§4).
 *
 * <p>Sessions used before the close ("Buy &amp; Use Same Day") kept a NULL Owner commission, because the
 * rate was not known; closing the month settles them ({@link CourseCommissionService#settle}).</p>
 */
@org.springframework.stereotype.Service
public class MonthlyCommissionClosingService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final MonthlyCommissionClosingRepository closings;
    private final CourseRepository courses;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;
    private final MonthlyTierSalesService tierSales;
    private final CourseCommissionService courseCommission;
    private final CommissionAllocationLedgerRepository allocations;

    public MonthlyCommissionClosingService(MonthlyCommissionClosingRepository closings, CourseRepository courses,
                                            CommissionSchemeResolver schemeResolver, CommissionTierResolver tierResolver,
                                            MonthlyTierSalesService tierSales, CourseCommissionService courseCommission,
                                            CommissionAllocationLedgerRepository allocations) {
        this.allocations = allocations;
        this.courseCommission = courseCommission;
        this.tierSales = tierSales;
        this.closings = closings;
        this.courses = courses;
        this.schemeResolver = schemeResolver;
        this.tierResolver = tierResolver;
    }

    @Transactional
    public MonthlyCommissionClosing closeMonth(String saleMonth, Staff staff, Branch branch, Permission permission, Long actorId) {
        require(saleMonth != null && saleMonth.matches("\\d{4}-(0[1-9]|1[0-2])"), "closingMonth ต้องเป็นรูปแบบ yyyy-mm");
        require(staff != null, "ต้องระบุพนักงาน");
        require(branch != null, "ต้องระบุสาขา");
        MonthlyCommissionClosing closing =
                closings.findByClosingMonthAndStaffIdAndBranchId(
                                saleMonth, staff.getId(), branch.getId())
                        .orElseGet(MonthlyCommissionClosing::new);

        if (closing.isClosed()) {
            throw new IllegalStateException(
                    "ปิดยอดเดือนนี้ของพนักงานคนนี้ไปแล้ว");
        }

        BigDecimal monthlySales = tierSales.netSales(
                staff.getId(), saleMonth, branch.getId());

        CommissionScheme scheme;
        if (closing.getId() == null) {
            // First close: select the scheme effective in the sale month.
            LocalDate effectiveDate = LocalDate.parse(saleMonth + "-01");
            scheme = schemeResolver.resolveEffective(effectiveDate);
        } else {
            // Reclose: preserve the original scheme and version.
            scheme = closing.getCommissionScheme();

            if (scheme == null
                    || closing.getCommissionSchemeVersion()
                    != scheme.getSchemeVersion()) {
                throw new IllegalStateException(
                        "ข้อมูล scheme ของงวดเดิมไม่ครบหรือเวอร์ชันไม่ตรง ต้องตรวจสอบก่อนปิดยอด");
            }
        }

        CommissionTier tier = tierResolver.resolve(scheme, monthlySales);
        closing.setClosingMonth(saleMonth);
        closing.setPermission(permission);
        closing.setStaff(staff);
        closing.setBranch(branch);
        closing.setMonthlyCourseSales(monthlySales);
        closing.setCommissionScheme(scheme);
        closing.setCommissionSchemeVersion(scheme.getSchemeVersion());
        closing.setCalculatedCommissionRate(tier.getCommissionRate());
        closing.setLockedCommissionRate(tier.getCommissionRate());
        closing.setClosingStatus(ClosingStatus.Closed);
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(actorId);
        MonthlyCommissionClosing saved = closings.save(closing);

        // each course is locked (row lock) like a usage does, so a session being recorded now is either settled here or
        // sees the locked rate itself
        for (Long courseId : courses.findIdsBySellerStaffIdAndSaleMonthAndBranchId(staff.getId(), saleMonth, branch.getId())) {
            Course course = courses.findByIdForUpdate(courseId).orElseThrow(() -> new IllegalStateException("ไม่พบคอร์ส"));
            lockCourse(course, scheme, tier, actorId);
        }

        return saved;
    }

    /** Locks the branch/month and reloads the latest closing state. */
    private MonthlyCommissionClosing findForUpdate(Long id) {
        MonthlyCommissionClosing closing = closings.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("ไม่พบรายการปิดยอด"));

        if (closing.getBranch() == null) {
            throw new IllegalStateException(
                    "งวดปิดยอดนี้ยังไม่มีสาขา ต้องตรวจข้อมูลก่อนทำรายการ");
        }

        closings.lockBranchMonth(
                closing.getBranch().getId(),
                closing.getClosingMonth());

        entityManager.refresh(
                closing,
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        return closing;
    }

    /**
     * Reopens a Closed month before it is paid (Manager): the rate of every course the seller sold that month is
     * unlocked and the Owner commission allocated at that rate is reversed; closing the month again works the tier out
     * again from the sales at that time and allocates a new version. Refused once the month is Finalized or any of its
     * Owner commission is in a Paid payment (a later correction is a pay adjustment).
     */
    @Transactional
    public MonthlyCommissionClosing reopen(Long closingId, String reason, Long actorId) {
        require(reason != null && !reason.isBlank(), "ต้องระบุเหตุผลที่ Reopen");
        MonthlyCommissionClosing closing = findForUpdate(closingId);
        if (closing.getClosingStatus() == ClosingStatus.Finalized) {
            throw new IllegalStateException("เดือนนี้ Finalize แล้ว ห้าม Reopen ให้บันทึกเป็น Adjustment ในงวดถัดไป");
        }
        if (closing.getClosingStatus() == ClosingStatus.Open) {
            throw new IllegalStateException("เดือนนี้ยังไม่ได้ปิดยอด");
        }
        Long staffId = closing.getStaff().getId();
        Branch branch = closing.getBranch();
        if (branch == null) {
            throw new IllegalStateException(
                    "งวดปิดยอดนี้ยังไม่มีสาขา ต้องตรวจข้อมูลเก่าก่อน Reopen");
        }
        if (allocations.ownerCommissionOfSaleMonthInBranch(
                staffId, closing.getClosingMonth(), branch.getId(),
                java.util.List.of(PaymentStatus.Paid))) {
            throw new IllegalStateException(
                    "ค่าคอมมิชชันของเดือนนี้ถูกจ่ายแล้ว ห้าม Reopen ให้บันทึกเป็น Adjustment ในงวดถัดไป");
        }

        for (Long courseId :
                courses.findIdsBySellerStaffIdAndSaleMonthAndBranchId(
                        staffId, closing.getClosingMonth(), branch.getId())) {
            Course course = courses.findByIdForUpdate(courseId)
                    .orElseThrow(() -> new IllegalStateException("ไม่พบคอร์ส"));

            courseCommission.unlock(
                    course,
                    "Reopen การปิดยอด " + closing.getClosingMonth()
                            + ": " + reason.trim(),
                    actorId);
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 255) throw new IllegalArgumentException("เหตุผลที่ Reopen ยาวเกิน 255 ตัวอักษร");
        closing.setClosingStatus(ClosingStatus.Open);
        closing.setClosedAt(null);
        closing.setClosedBy(null);
        closing.setLockedCommissionRate(null);
        closing.setReopenReason(trimmed);
        closing.setReopenedBy(actorId);
        closing.setReopenedAt(Instant.now());
        return closings.save(closing);
    }

    /** Closed -> Finalized, together with the payment of the money that depends on its rate. */
    @Transactional
    public MonthlyCommissionClosing finalizeClosing(MonthlyCommissionClosing closing, Long actorId) {
        closing = findForUpdate(closing.getId());

        if (closing.getClosingStatus() == ClosingStatus.Finalized) {
            return closing;
        }
        if (closing.getClosingStatus() != ClosingStatus.Closed) {
            throw new IllegalStateException("เดือนขาย " + closing.getClosingMonth() + " ยังไม่ได้ปิดยอด จึง Finalize ไม่ได้");
        }
        closing.setClosingStatus(ClosingStatus.Finalized);
        closing.setFinalizedAt(Instant.now());
        closing.setFinalizedBy(actorId);
        return closings.save(closing);
    }

    private void lockCourse(Course course, CommissionScheme scheme, CommissionTier tier, Long actorId) {
        BigDecimal pool = course.getCoursePrice().multiply(tier.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perVisit = CommissionAllocationCalculator.nominal(pool, course.getTotalVisits());

        course.setCommissionStatus(CommissionStatus.Locked);
        course.setCommissionScheme(scheme);
        course.setCommissionSchemeVersion(scheme.getSchemeVersion());
        course.setLockedCommissionRate(tier.getCommissionRate());
        course.setTotalCourseCommissionPool(pool);
        course.setCommissionAllocationPerVisit(perVisit);
        course.setCommissionGeneratedDate(ClinicTime.today());
        courses.save(course);
        // sessions used before the rate was known get their rate, Owner commission and allocation now
        courseCommission.settle(course, actorId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
