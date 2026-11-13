package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.commission.CommissionSchemeResolver;
import com.sabyeguyz.clinicv2.commission.CommissionTier;
import com.sabyeguyz.clinicv2.commission.CommissionTierResolver;
import com.sabyeguyz.clinicv2.commission.MonthlyTierSalesService;
import com.sabyeguyz.clinicv2.commission.BranchMonthlyClosingService;
import com.sabyeguyz.clinicv2.course.CourseEntitlement;
import com.sabyeguyz.clinicv2.course.CourseEntitlementRepository;
import com.sabyeguyz.clinicv2.course.CourseLedger;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.course.CourseSharedMember;
import com.sabyeguyz.clinicv2.course.CourseSharedMemberRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseMemberRole;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sells a Course (Clinic ERP Workflow Brief §5, Commission_System_Requirement_Dev §§2-5).
 *
 * <p>Per the "Critical Rule: Commission Rate Must Follow the Course", the
 * commission scheme/tier is resolved and stamped onto the Course at sale
 * time. Only {@code provisionalCommissionRate} is set here — the pool and
 * per-visit allocation are computed later by Monthly Closing, which also
 * freezes {@code lockedCommissionRate} (see {@code MonthlyClosingService}
 * once that module is built). Until then this Course's commission is
 * Provisional and may still move with the seller's running monthly total.</p>
 */
@org.springframework.stereotype.Service
public class CourseService {
    private static final DateTimeFormatter SALE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CourseRepository courses;
    private final CourseLedgerRepository ledger;
    private final CourseSharedMemberRepository sharedMembers;
    private final CourseEntitlementRepository entitlements;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;
    private final MonthlyTierSalesService tierSales;
    private final BranchMonthlyClosingService branchClosingService;

    public CourseService(CourseRepository courses, CourseLedgerRepository ledger,
                         CourseSharedMemberRepository sharedMembers, CourseEntitlementRepository entitlements,
                         CommissionSchemeResolver schemeResolver, CommissionTierResolver tierResolver,
                         MonthlyTierSalesService tierSales, BranchMonthlyClosingService branchClosingService) {
        this.courses = courses;
        this.ledger = ledger;
        this.sharedMembers = sharedMembers;
        this.entitlements = entitlements;
        this.schemeResolver = schemeResolver;
        this.tierResolver = tierResolver;
        this.tierSales = tierSales;
        this.branchClosingService = branchClosingService;
    }

    /**
     * The course price is the sale line's commissionable amount (Course.course_price must equal the sale line). The
     * seller is the Owner PT and gets the whole sale credit (Course.seller_staff_id is the single field for both).
     */
    @Transactional
    public Course sell(Receipt receipt, SalesTransaction transaction, Service serviceTemplate, Staff sellerStaff,
                        Integer totalVisitsOverride, Integer bonusVisitsOverride, LocalDate saleDate, Long actorId) {
        require(receipt != null, "ต้องระบุใบเสร็จ");
        require(transaction != null, "ต้องระบุรายการขาย");
        require(serviceTemplate != null, "ต้องระบุบริการ/คอร์สต้นแบบ");
        require(sellerStaff != null, "ต้องระบุผู้ขาย");
        require(transaction.getTransactionType() == TransactionType.CoursePurchase,
                "รายการขายต้องเป็นประเภท CoursePurchase");
        require(!courses.existsBySaleTransactionId(transaction.getId()),
                "รายการขายนี้มีคอร์สผูกอยู่แล้ว (1 คอร์สต่อ 1 รายการขาย)");
        require(sameReceipt(transaction.getReceipt(), receipt), "รายการขายต้องอยู่ในใบเสร็จเดียวกับคอร์ส");
        Patient purchaser = transaction.getPatient();
        require(purchaser != null && samePatient(purchaser, receipt.getPatient()),
                "ผู้ซื้อต้องตรงกับผู้ป่วยในรายการขายและใบเสร็จ");
        LocalDate effectiveSaleDate = saleDate == null ? ClinicTime.today() : saleDate;

        BigDecimal price = transaction.getCommissionableAmount();
        Integer totalVisits = totalVisitsOverride != null ? totalVisitsOverride : serviceTemplate.getDefaultTotalVisits();
        LocalDate expiryDate = serviceTemplate.expiryFrom(effectiveSaleDate);
        require(totalVisits != null && totalVisits >= 1, "ต้องระบุจำนวนครั้งของคอร์ส");
        require(price != null && price.signum() >= 0, "ราคาคอร์สต้องไม่ติดลบ");

        String saleMonth = effectiveSaleDate.format(SALE_MONTH);
        require(transaction.getBranch() != null, "ต้องระบุสาขาของรายการขาย");

        branchClosingService.requireCourseSaleOpen(
                transaction.getBranch(),
                saleMonth,
                sellerStaff.getId());

        long existingOnReceipt = courses.countByReceiptId(receipt.getId());
        require(existingOnReceipt < 99, "ซื้อคอร์สใหม่ได้ไม่เกิน 99 ชุดต่อใบเสร็จ");
        int courseSequence = (int) existingOnReceipt + 1;
        if (courseSequence == 2) {
            // Once a receipt contains multiple sets, every new course gets its
            // two-digit suffix, including the first set created in this sale.
            for (Course existing : courses.findByReceiptIdOrderByCourseSequenceAsc(receipt.getId())) {
                if (existing.getCourseSequence() == 1 && existing.getCourseNo().equals(receipt.getReceiptNo())) {
                    existing.setCourseNo(receipt.getReceiptNo() + "-01");
                    courses.save(existing);
                }
            }
        }
        String courseNo = courseSequence == 1 ? receipt.getReceiptNo()
                : "%s-%02d".formatted(receipt.getReceiptNo(), courseSequence);

        CommissionScheme scheme = schemeResolver.resolveEffective(effectiveSaleDate);

        BigDecimal monthlySalesToDate = tierSales.netSales(
                        sellerStaff.getId(),
                        saleMonth,
                        transaction.getBranch().getId())
                .add(price);
        CommissionTier tier = tierResolver.resolve(scheme, monthlySalesToDate);

        int totalUsableVisits = totalVisits;

        Course course = new Course();
        course.setBranch(transaction.getBranch());
        course.setValidityValue(serviceTemplate.getDefaultValidityValue());
        course.setValidityUnit(serviceTemplate.getDefaultValidityUnit());
        course.setCourseNo(courseNo);
        course.setCourseSequence(courseSequence);
        course.setCourseName(serviceTemplate.getServiceName());
        course.setCoursePrice(price);
        course.setTotalVisits(totalUsableVisits);
        // Total visits already includes promotional sessions.
        course.setBonusVisits(0);
        course.setExpiryDate(expiryDate);
        course.setCourseStatus(CourseStatus.Active);
        course.setService(serviceTemplate);
        course.setCourseCreateAt(Instant.now());
        course.setCourseCreateBy(actorId);
        course.setReceipt(receipt);
        course.setPurchaserPatient(purchaser);
        course.setSaleTransaction(transaction);
        course.setSaleMonth(saleMonth);
        course.setSellerStaff(sellerStaff);
        course.setCommissionStatus(CommissionStatus.Provisional);
        course.setCommissionScheme(scheme);
        course.setCommissionSchemeVersion(scheme.getSchemeVersion());
        course.setProvisionalCommissionRate(tier.getCommissionRate());
        course.setVisitsUsed(0);
        course.setRemainingVisits(totalUsableVisits);
        course.setOwnerNetCommissionReleasedTotal(BigDecimal.ZERO);
        course.setSubstituteTreatmentFeeTotal(BigDecimal.ZERO);
        // The commission rate of the sale month is not known until the month is closed: no outstanding amount yet.
        course.setCommissionOutstanding(null);
        course.setTreatmentFeeBaseAmount(transaction.getAmount().subtract(transaction.getAllocatedBillDiscountAmount()));
        Course saved = courses.save(course);

        sharedMembers.save(new CourseSharedMember(saved, purchaser, CourseMemberRole.Owner));
        entitlements.save(new CourseEntitlement(saved, purchaser, totalUsableVisits));

        CourseLedger purchase = new CourseLedger();
        purchase.setCourse(saved);
        purchase.setPatient(purchaser);
        purchase.setEntryType(CourseLedgerEntryType.Purchase);
        purchase.setQtyChange(totalUsableVisits);
        purchase.setBalanceAfter(totalUsableVisits);
        purchase.setSalesTransaction(transaction);
        purchase.setLedgerCreateAt(Instant.now());
        purchase.setLedgerCreateBy(actorId);
        ledger.save(purchase);

        return saved;
    }

    private static boolean samePatient(Patient a, Patient b) {
        return a == b || (a != null && b != null && a.getId() != null && a.getId().equals(b.getId()));
    }

    private static boolean sameReceipt(Receipt a, Receipt b) {
        return a == b || (a != null && b != null && a.getId() != null && a.getId().equals(b.getId()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
