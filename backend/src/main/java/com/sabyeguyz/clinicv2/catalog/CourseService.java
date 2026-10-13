package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.commission.CommissionSchemeResolver;
import com.sabyeguyz.clinicv2.commission.CommissionTier;
import com.sabyeguyz.clinicv2.commission.CommissionTierResolver;
import com.sabyeguyz.clinicv2.course.CourseEntitlement;
import com.sabyeguyz.clinicv2.course.CourseEntitlementRepository;
import com.sabyeguyz.clinicv2.course.CourseLedger;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.course.CourseSaleStaffShare;
import com.sabyeguyz.clinicv2.course.CourseSaleStaffShareRepository;
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
    private final CourseSaleStaffShareRepository saleShares;
    private final CourseSharedMemberRepository sharedMembers;
    private final CourseEntitlementRepository entitlements;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;

    public CourseService(CourseRepository courses, CourseLedgerRepository ledger,
                          CourseSaleStaffShareRepository saleShares, CourseSharedMemberRepository sharedMembers,
                          CourseEntitlementRepository entitlements, CommissionSchemeResolver schemeResolver,
                          CommissionTierResolver tierResolver) {
        this.courses = courses;
        this.ledger = ledger;
        this.saleShares = saleShares;
        this.sharedMembers = sharedMembers;
        this.entitlements = entitlements;
        this.schemeResolver = schemeResolver;
        this.tierResolver = tierResolver;
    }

    /** One staff member's percentage of a course sale's credit (percents of a sale must total 100). */
    public record SaleShare(Staff staff, BigDecimal percent) {}

    /** The course price is the sale line's amount (Course.course_price must equal the sale line). */
    @Transactional
    public Course sell(Receipt receipt, SalesTransaction transaction, Service serviceTemplate, Staff sellerStaff,
                        Staff caseOwnerStaff, Integer totalVisitsOverride, Integer bonusVisitsOverride,
                        List<SaleShare> shares, LocalDate saleDate, Long actorId) {
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
        LocalDate effectiveSaleDate = saleDate == null ? LocalDate.now() : saleDate;

        BigDecimal price = transaction.getCommissionableAmount();
        Integer totalVisits = totalVisitsOverride != null ? totalVisitsOverride : serviceTemplate.getDefaultTotalVisits();
        Integer bonusVisits = bonusVisitsOverride != null ? bonusVisitsOverride : serviceTemplate.getDefaultBonusVisits();
        require(totalVisits != null && totalVisits >= 1, "ต้องระบุจำนวนครั้งของคอร์ส");
        LocalDate expiryDate = serviceTemplate.expiryFrom(effectiveSaleDate);
        require(price != null && price.signum() >= 0, "ราคาคอร์สต้องไม่ติดลบ");

        Staff owner = caseOwnerStaff != null ? caseOwnerStaff : sellerStaff;
        String saleMonth = effectiveSaleDate.format(SALE_MONTH);

        long existingOnReceipt = courses.countByReceiptId(receipt.getId());
        int courseSequence = (int) existingOnReceipt + 1;
        String courseNo = courseSequence == 1 ? receipt.getReceiptNo()
                : "%s-%02d".formatted(receipt.getReceiptNo(), courseSequence);

        CommissionScheme scheme = schemeResolver.resolveEffective(effectiveSaleDate);
        List<SaleShare> effectiveShares = shares == null || shares.isEmpty()
                ? List.of(new SaleShare(sellerStaff, HUNDRED)) : shares;
        List<BigDecimal> credits = creditedAmounts(effectiveShares, price);
        BigDecimal sellerCredit = BigDecimal.ZERO;
        for (int i = 0; i < effectiveShares.size(); i++) {
            if (samePerson(effectiveShares.get(i).staff(), sellerStaff)) {
                sellerCredit = credits.get(i);
            }
        }
        BigDecimal monthlySalesToDate = courses.sumCreditedSalesByStaffAndSaleMonth(sellerStaff.getId(), saleMonth)
                .add(sellerCredit);
        CommissionTier tier = tierResolver.resolve(scheme, monthlySalesToDate);

        int totalUsableVisits = totalVisits + (bonusVisits == null ? 0 : bonusVisits);

        Course course = new Course();
        course.setBranch(transaction.getBranch());
        course.setValidityValue(serviceTemplate.getDefaultValidityValue());
        course.setValidityUnit(serviceTemplate.getDefaultValidityUnit());
        course.setCourseNo(courseNo);
        course.setCourseSequence(courseSequence);
        course.setCourseName(serviceTemplate.getServiceName());
        course.setCoursePrice(price);
        course.setTotalVisits(totalUsableVisits);
        course.setBonusVisits(bonusVisits);
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
        course.setCaseOwnerStaff(owner);
        course.setCommissionStatus(CommissionStatus.Provisional);
        course.setCommissionScheme(scheme);
        course.setCommissionSchemeVersion(scheme.getSchemeVersion());
        course.setProvisionalCommissionRate(tier.getCommissionRate());
        course.setVisitsUsed(0);
        course.setRemainingVisits(totalUsableVisits);
        course.setGrossCommissionAllocatedTotal(BigDecimal.ZERO);
        course.setOwnerNetCommissionReleasedTotal(BigDecimal.ZERO);
        course.setSubstituteTreatmentFeeTotal(BigDecimal.ZERO);
        course.setCommissionOutstanding(BigDecimal.ZERO);
        Course saved = courses.save(course);

        for (int i = 0; i < effectiveShares.size(); i++) {
            SaleShare share = effectiveShares.get(i);
            saleShares.save(new CourseSaleStaffShare(saved, share.staff(), share.percent().setScale(2, RoundingMode.HALF_UP),
                    credits.get(i), actorId));
        }
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

    /** Percents must be positive, distinct per staff and total 100; credits total the price, the last taking the remainder. */
    private static List<BigDecimal> creditedAmounts(List<SaleShare> shares, BigDecimal price) {
        Set<Object> seen = new HashSet<>();
        BigDecimal totalPercent = BigDecimal.ZERO;
        for (SaleShare share : shares) {
            require(share.staff() != null, "ต้องระบุพนักงานที่ได้รับเครดิตยอดขาย");
            require(share.percent() != null && share.percent().signum() > 0 && share.percent().compareTo(HUNDRED) <= 0,
                    "สัดส่วนยอดขายต้องมากกว่า 0 และไม่เกิน 100");
            Object key = share.staff().getId() != null ? share.staff().getId() : share.staff();
            require(seen.add(key), "พนักงานคนเดียวกันซ้ำในสัดส่วนยอดขาย");
            totalPercent = totalPercent.add(share.percent());
        }
        require(totalPercent.compareTo(HUNDRED) == 0, "สัดส่วนยอดขายรวมกันต้องเท่ากับ 100");

        List<BigDecimal> credits = new ArrayList<>();
        BigDecimal remaining = price;
        for (int i = 0; i < shares.size(); i++) {
            BigDecimal credit = i == shares.size() - 1 ? remaining
                    : price.multiply(shares.get(i).percent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            credits.add(credit);
            remaining = remaining.subtract(credit);
        }
        return credits;
    }

    private static boolean samePerson(Staff a, Staff b) {
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
