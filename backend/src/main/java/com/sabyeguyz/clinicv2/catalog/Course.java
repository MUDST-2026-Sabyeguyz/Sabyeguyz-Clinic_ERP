package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Commission fields follow Commission_System_Requirement_Dev: the rate is
 * Provisional until Monthly Closing, then Locked/frozen on the course forever
 * (see {@link com.sabyeguyz.clinicv2.commission.MonthlyCommissionClosing}).
 */
@Entity
@Table(name = "course")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long id;

    /** Business format: RE-xxxxxxx or RE-xxxxxxx-NN. */
    @Column(name = "course_no", length = 30, nullable = false, unique = true)
    private String courseNo;

    /** Sequence within the receipt, when more than one Course is on the same receipt. */
    @Column(name = "course_sequence", nullable = false)
    private int courseSequence = 1;

    @Column(name = "course_name", length = 255, nullable = false)
    private String courseName;

    @Column(name = "course_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal coursePrice;

    @Column(name = "total_visits", nullable = false)
    private int totalVisits;

    @Column(name = "bonus_visits")
    private Integer bonusVisits;

    /** = sale_date + service.default_validity_days, computed once at sale time then frozen. */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "course_status", nullable = false)
    private CourseStatus courseStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "course_create_at", nullable = false, updatable = false)
    private Instant courseCreateAt;

    @Column(name = "course_create_by", nullable = false, updatable = false)
    private Long courseCreateBy;

    @Column(name = "course_update_at")
    private Instant courseUpdateAt;

    @Column(name = "course_update_by")
    private Long courseUpdateBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    /** yyyy-mm, used to aggregate monthly sales for Tier calc. */
    @Column(name = "sale_month", length = 7, nullable = false)
    private String saleMonth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_staff_id")
    private Staff sellerStaff;

    /** Commission Pool Owner (default = seller). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_owner_staff_id")
    private Staff caseOwnerStaff;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "commission_status", nullable = false)
    private CommissionStatus commissionStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commission_scheme_id")
    private CommissionScheme commissionScheme;

    /** MUST be frozen together with commissionScheme so a later Tier edit never changes this course's historical rate. */
    @Column(name = "commission_scheme_version", nullable = false)
    private int commissionSchemeVersion;

    @Column(name = "provisional_commission_rate", precision = 5, scale = 4)
    private BigDecimal provisionalCommissionRate;

    /** Frozen after Monthly Closing — never changes afterwards. */
    @Column(name = "locked_commission_rate", precision = 5, scale = 4)
    private BigDecimal lockedCommissionRate;

    /** course_price x locked_commission_rate. */
    @Column(name = "total_course_commission_pool", precision = 12, scale = 2)
    private BigDecimal totalCourseCommissionPool;

    /** total_course_commission_pool / total_visits. */
    @Column(name = "commission_allocation_per_visit", precision = 12, scale = 2)
    private BigDecimal commissionAllocationPerVisit;

    /** Date the commission pool was generated (Monthly Closing date). */
    @Column(name = "commission_generated_date")
    private LocalDate commissionGeneratedDate;

    @Column(name = "visits_used", nullable = false)
    private int visitsUsed = 0;

    @Column(name = "remaining_visits", nullable = false)
    private int remainingVisits;

    @Column(name = "gross_commission_allocated_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal grossCommissionAllocatedTotal = BigDecimal.ZERO;

    @Column(name = "owner_net_commission_released_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal ownerNetCommissionReleasedTotal = BigDecimal.ZERO;

    @Column(name = "substitute_treatment_fee_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal substituteTreatmentFeeTotal = BigDecimal.ZERO;

    /** total_course_commission_pool - gross_commission_allocated_total. */
    @Column(name = "commission_outstanding", precision = 12, scale = 2, nullable = false)
    private BigDecimal commissionOutstanding = BigDecimal.ZERO;

    /** Original course buyer and initial owner, independent of shared members. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchaser_patient_id")
    private Patient purchaserPatient;

    /** The one sale line that created this course (one course per sale line). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_transaction_id", unique = true)
    private SalesTransaction saleTransaction;

    /** total_visits + bonus_visits (null bonus counts as 0); a GENERATED ALWAYS column, so never written. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "entitled_visits", insertable = false, updatable = false)
    private int entitledVisits;

    public Course() {}

    @PrePersist
    @PreUpdate
    void syncEntitledVisits() {
        this.entitledVisits = totalVisits + (bonusVisits == null ? 0 : bonusVisits);
    }

    public Long getId() { return id; }
    public String getCourseNo() { return courseNo; }
    public void setCourseNo(String courseNo) { this.courseNo = courseNo; }
    public int getCourseSequence() { return courseSequence; }
    public void setCourseSequence(int courseSequence) { this.courseSequence = courseSequence; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public BigDecimal getCoursePrice() { return coursePrice; }
    public void setCoursePrice(BigDecimal coursePrice) { this.coursePrice = coursePrice; }
    public int getTotalVisits() { return totalVisits; }
    public void setTotalVisits(int totalVisits) { this.totalVisits = totalVisits; syncEntitledVisits(); }
    public Integer getBonusVisits() { return bonusVisits; }
    public void setBonusVisits(Integer bonusVisits) { this.bonusVisits = bonusVisits; syncEntitledVisits(); }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public CourseStatus getCourseStatus() { return courseStatus; }
    public void setCourseStatus(CourseStatus courseStatus) { this.courseStatus = courseStatus; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public Instant getCourseCreateAt() { return courseCreateAt; }
    public void setCourseCreateAt(Instant courseCreateAt) { this.courseCreateAt = courseCreateAt; }
    public Long getCourseCreateBy() { return courseCreateBy; }
    public void setCourseCreateBy(Long courseCreateBy) { this.courseCreateBy = courseCreateBy; }
    public Instant getCourseUpdateAt() { return courseUpdateAt; }
    public void setCourseUpdateAt(Instant courseUpdateAt) { this.courseUpdateAt = courseUpdateAt; }
    public Long getCourseUpdateBy() { return courseUpdateBy; }
    public void setCourseUpdateBy(Long courseUpdateBy) { this.courseUpdateBy = courseUpdateBy; }
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    public String getSaleMonth() { return saleMonth; }
    public void setSaleMonth(String saleMonth) { this.saleMonth = saleMonth; }
    public Staff getSellerStaff() { return sellerStaff; }
    public void setSellerStaff(Staff sellerStaff) { this.sellerStaff = sellerStaff; }
    public Staff getCaseOwnerStaff() { return caseOwnerStaff; }
    public void setCaseOwnerStaff(Staff caseOwnerStaff) { this.caseOwnerStaff = caseOwnerStaff; }
    public CommissionStatus getCommissionStatus() { return commissionStatus; }
    public void setCommissionStatus(CommissionStatus commissionStatus) { this.commissionStatus = commissionStatus; }
    public CommissionScheme getCommissionScheme() { return commissionScheme; }
    public void setCommissionScheme(CommissionScheme commissionScheme) { this.commissionScheme = commissionScheme; }
    public int getCommissionSchemeVersion() { return commissionSchemeVersion; }
    public void setCommissionSchemeVersion(int commissionSchemeVersion) { this.commissionSchemeVersion = commissionSchemeVersion; }
    public BigDecimal getProvisionalCommissionRate() { return provisionalCommissionRate; }
    public void setProvisionalCommissionRate(BigDecimal provisionalCommissionRate) { this.provisionalCommissionRate = provisionalCommissionRate; }
    public BigDecimal getLockedCommissionRate() { return lockedCommissionRate; }
    public void setLockedCommissionRate(BigDecimal lockedCommissionRate) { this.lockedCommissionRate = lockedCommissionRate; }
    public BigDecimal getTotalCourseCommissionPool() { return totalCourseCommissionPool; }
    public void setTotalCourseCommissionPool(BigDecimal totalCourseCommissionPool) { this.totalCourseCommissionPool = totalCourseCommissionPool; }
    public BigDecimal getCommissionAllocationPerVisit() { return commissionAllocationPerVisit; }
    public void setCommissionAllocationPerVisit(BigDecimal commissionAllocationPerVisit) { this.commissionAllocationPerVisit = commissionAllocationPerVisit; }
    public LocalDate getCommissionGeneratedDate() { return commissionGeneratedDate; }
    public void setCommissionGeneratedDate(LocalDate commissionGeneratedDate) { this.commissionGeneratedDate = commissionGeneratedDate; }
    public int getVisitsUsed() { return visitsUsed; }
    public void setVisitsUsed(int visitsUsed) { this.visitsUsed = visitsUsed; }
    public int getRemainingVisits() { return remainingVisits; }
    public void setRemainingVisits(int remainingVisits) { this.remainingVisits = remainingVisits; }
    public BigDecimal getGrossCommissionAllocatedTotal() { return grossCommissionAllocatedTotal; }
    public void setGrossCommissionAllocatedTotal(BigDecimal grossCommissionAllocatedTotal) { this.grossCommissionAllocatedTotal = grossCommissionAllocatedTotal; }
    public BigDecimal getOwnerNetCommissionReleasedTotal() { return ownerNetCommissionReleasedTotal; }
    public void setOwnerNetCommissionReleasedTotal(BigDecimal ownerNetCommissionReleasedTotal) { this.ownerNetCommissionReleasedTotal = ownerNetCommissionReleasedTotal; }
    public BigDecimal getSubstituteTreatmentFeeTotal() { return substituteTreatmentFeeTotal; }
    public void setSubstituteTreatmentFeeTotal(BigDecimal substituteTreatmentFeeTotal) { this.substituteTreatmentFeeTotal = substituteTreatmentFeeTotal; }
    public BigDecimal getCommissionOutstanding() { return commissionOutstanding; }
    public void setCommissionOutstanding(BigDecimal commissionOutstanding) { this.commissionOutstanding = commissionOutstanding; }
    public Patient getPurchaserPatient() { return purchaserPatient; }
    public void setPurchaserPatient(Patient purchaserPatient) { this.purchaserPatient = purchaserPatient; }
    public SalesTransaction getSaleTransaction() { return saleTransaction; }
    public void setSaleTransaction(SalesTransaction saleTransaction) { this.saleTransaction = saleTransaction; }
    public int getEntitledVisits() { return entitledVisits; }
}
