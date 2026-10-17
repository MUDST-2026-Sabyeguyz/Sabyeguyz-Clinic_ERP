package com.sabyeguyz.clinic.course;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.staff.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * ใบซื้อคอร์สหนึ่งใบ เก็บ snapshot ของ course master ณ วันที่ขาย
 * ยอดคงเหลือไม่ได้อยู่ในตารางนี้ — คำนวณจากผลรวมของ {@link CourseMovement}
 */
@Entity
@Table(name = "course_purchases")
public class CoursePurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "course_purchase_id_generator")
    @SequenceGenerator(name = "course_purchase_id_generator", sequenceName = "course_purchase_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "course_name")
    private String courseName;

    private BigDecimal price;

    private int sessions;

    @Column(name = "bonus_sessions")
    private int bonusSessions;

    @Column(name = "purchased_at")
    private Instant purchasedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CoursePurchaseStatus status;

    @Column(name = "created_at")
    private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="seller_employee_id") private Staff seller;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="case_owner_employee_id") private Staff caseOwner;
    @Column(name="sale_month") private java.time.LocalDate saleMonth;
    @Column(name="commission_status") private String commissionStatus="PROVISIONAL";
    @Column(name="commission_scheme_id") private Long commissionSchemeId; @Column(name="commission_scheme_version") private String commissionSchemeVersion;
    @Column(name="provisional_commission_rate") private BigDecimal provisionalCommissionRate; @Column(name="locked_commission_rate") private BigDecimal lockedCommissionRate;
    @Column(name="total_course_commission_pool") private BigDecimal totalCourseCommissionPool; @Column(name="commission_allocation_per_visit") private BigDecimal commissionAllocationPerVisit;

    protected CoursePurchase() {}

    public CoursePurchase(Patient patient, Course course, Branch branch, Instant purchasedAt) {
        this.patient = patient;
        this.course = course;
        this.branch = branch;
        this.courseCode = course.getCode();
        this.courseName = course.getName();
        this.price = course.getPrice();
        this.sessions = course.getSessions();
        this.bonusSessions = course.getBonusSessions();
        this.purchasedAt = purchasedAt;
        this.saleMonth = purchasedAt.atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate().withDayOfMonth(1);
        this.expiresAt = purchasedAt.plus(Duration.ofDays(course.getValidityDays()));
        this.status = CoursePurchaseStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static CoursePurchase transferredFrom(CoursePurchase source, Patient destination, int quantity) {
        CoursePurchase transfer = new CoursePurchase();
        transfer.patient = destination;
        transfer.course = source.course;
        transfer.branch = source.branch;
        transfer.courseCode = source.courseCode;
        transfer.courseName = source.courseName;
        transfer.price = source.price;
        transfer.sessions = quantity;
        transfer.bonusSessions = 0;
        transfer.purchasedAt = source.purchasedAt;
        transfer.expiresAt = source.expiresAt;
        transfer.status = CoursePurchaseStatus.ACTIVE;
        transfer.createdAt = Instant.now();
        return transfer;
    }

    /** จำนวนครั้งทั้งหมดที่ซื้อ รวม Bonus */
    public int totalSessions() {
        return sessions + bonusSessions;
    }

    public boolean isExpiredAt(Instant moment) {
        return !moment.isBefore(expiresAt);
    }

    public void cancel() {
        this.status = CoursePurchaseStatus.CANCELLED;
    }
    public void lockCommission(Long schemeId,String version,BigDecimal rate){commissionSchemeId=schemeId;commissionSchemeVersion=version;lockedCommissionRate=rate;totalCourseCommissionPool=price.multiply(rate);commissionAllocationPerVisit=totalCourseCommissionPool.divide(BigDecimal.valueOf(totalSessions()),2,java.math.RoundingMode.HALF_UP);commissionStatus="LOCKED";}
    public void assignSeller(Staff staff){seller=staff;caseOwner=staff;} public Staff getSeller(){return seller;} public Staff getCaseOwner(){return caseOwner;} public java.time.LocalDate getSaleMonth(){return saleMonth;} public String getCommissionStatus(){return commissionStatus;} public BigDecimal getLockedCommissionRate(){return lockedCommissionRate;} public BigDecimal getTotalCourseCommissionPool(){return totalCourseCommissionPool;} public BigDecimal getCommissionAllocationPerVisit(){return commissionAllocationPerVisit;}

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public Course getCourse() { return course; }
    public Branch getBranch() { return branch; }
    public String getCourseCode() { return courseCode; }
    public String getCourseName() { return courseName; }
    public BigDecimal getPrice() { return price; }
    public int getSessions() { return sessions; }
    public int getBonusSessions() { return bonusSessions; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public CoursePurchaseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
