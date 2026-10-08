package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.commission.CommissionSchemeResolver;
import com.sabyeguyz.clinicv2.commission.CommissionTier;
import com.sabyeguyz.clinicv2.commission.CommissionTierResolver;
import com.sabyeguyz.clinicv2.course.CourseLedger;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private final CourseRepository courses;
    private final CourseLedgerRepository ledger;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;

    public CourseService(CourseRepository courses, CourseLedgerRepository ledger,
                          CommissionSchemeResolver schemeResolver, CommissionTierResolver tierResolver) {
        this.courses = courses;
        this.ledger = ledger;
        this.schemeResolver = schemeResolver;
        this.tierResolver = tierResolver;
    }

    /** The course price is the sale line's amount (Course.course_price must equal the sale line). */
    @Transactional
    public Course sell(Receipt receipt, SalesTransaction transaction, Service serviceTemplate, Staff sellerStaff,
                        Staff caseOwnerStaff, Integer totalVisitsOverride, Integer bonusVisitsOverride,
                        LocalDate saleDate, Long actorId) {
        require(receipt != null, "ต้องระบุใบเสร็จ");
        require(transaction != null, "ต้องระบุรายการขาย");
        require(serviceTemplate != null, "ต้องระบุบริการ/คอร์สต้นแบบ");
        require(sellerStaff != null, "ต้องระบุผู้ขาย");
        require(transaction.getTransactionType() == TransactionType.CoursePurchase,
                "รายการขายต้องเป็นประเภท CoursePurchase");
        require(transaction.getCourse() == null && !courses.existsBySaleTransactionId(transaction.getId()),
                "รายการขายนี้มีคอร์สผูกอยู่แล้ว (1 คอร์สต่อ 1 รายการขาย)");
        Patient purchaser = transaction.getPatient();
        require(purchaser != null && samePatient(purchaser, receipt.getPatient()),
                "ผู้ซื้อต้องตรงกับผู้ป่วยในรายการขายและใบเสร็จ");
        LocalDate effectiveSaleDate = saleDate == null ? LocalDate.now() : saleDate;

        BigDecimal price = transaction.getAmount();
        Integer totalVisits = totalVisitsOverride != null ? totalVisitsOverride : serviceTemplate.getDefaultTotalVisits();
        Integer bonusVisits = bonusVisitsOverride != null ? bonusVisitsOverride : serviceTemplate.getDefaultBonusVisits();
        Integer validityDays = serviceTemplate.getDefaultValidityDays();
        require(totalVisits != null && totalVisits >= 1, "ต้องระบุจำนวนครั้งของคอร์ส");
        require(validityDays != null, "บริการนี้ไม่ได้ตั้งค่าอายุคอร์ส (default_validity_days)");
        require(price != null && price.signum() >= 0, "ราคาคอร์สต้องไม่ติดลบ");

        Staff owner = caseOwnerStaff != null ? caseOwnerStaff : sellerStaff;
        String saleMonth = effectiveSaleDate.format(SALE_MONTH);

        long existingOnReceipt = courses.countByReceiptId(receipt.getId());
        int courseSequence = (int) existingOnReceipt + 1;
        String courseNo = courseSequence == 1 ? receipt.getReceiptNo()
                : "%s-%02d".formatted(receipt.getReceiptNo(), courseSequence);

        CommissionScheme scheme = schemeResolver.resolveEffective(effectiveSaleDate);
        BigDecimal monthlySalesToDate = courses.sumCoursePriceBySellerAndSaleMonth(sellerStaff.getId(), saleMonth)
                .add(price);
        CommissionTier tier = tierResolver.resolve(scheme, monthlySalesToDate);

        int totalUsableVisits = totalVisits + (bonusVisits == null ? 0 : bonusVisits);

        Course course = new Course();
        course.setCourseNo(courseNo);
        course.setCourseSequence(courseSequence);
        course.setCourseName(serviceTemplate.getServiceName());
        course.setCoursePrice(price);
        course.setTotalVisits(totalVisits);
        course.setBonusVisits(bonusVisits);
        course.setExpiryDate(effectiveSaleDate.plusDays(validityDays));
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

        CourseLedger purchase = new CourseLedger();
        purchase.setCourse(saved);
        purchase.setPatient(purchaser);
        purchase.setEntryType(CourseLedgerEntryType.Purchase);
        purchase.setQtyChange(totalUsableVisits);
        purchase.setBalanceAfter(totalUsableVisits);
        purchase.setReferenceType("RECEIPT");
        purchase.setReferenceId(receipt.getId());
        purchase.setLedgerCreateAt(Instant.now());
        purchase.setLedgerCreateBy(actorId);
        ledger.save(purchase);

        return saved;
    }

    private static boolean samePatient(Patient a, Patient b) {
        return a == b || (a != null && b != null && a.getId() != null && a.getId().equals(b.getId()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
