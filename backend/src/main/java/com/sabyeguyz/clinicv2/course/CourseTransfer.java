package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.patient.Patient;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "course_transfer")
public class CourseTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** Patient transferring out (current owner). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_patient_id")
    private Patient fromPatient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_patient_id")
    private Patient toPatient;

    @Column(name = "qty_transferred", nullable = false)
    private int qtyTransferred;

    @Column(name = "transfer_date", nullable = false)
    private Instant transferDate;

    @Column(length = 255)
    private String remark;

    @Column(name = "transfer_create_at", nullable = false, updatable = false)
    private Instant transferCreateAt;

    @Column(name = "transfer_create_by", nullable = false, updatable = false)
    private Long transferCreateBy;

    @Column(name = "transfer_update_at")
    private Instant transferUpdateAt;

    @Column(name = "transfer_update_by")
    private Long transferUpdateBy;

    public CourseTransfer() {}

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Patient getFromPatient() { return fromPatient; }
    public void setFromPatient(Patient fromPatient) { this.fromPatient = fromPatient; }
    public Patient getToPatient() { return toPatient; }
    public void setToPatient(Patient toPatient) { this.toPatient = toPatient; }
    public int getQtyTransferred() { return qtyTransferred; }
    public void setQtyTransferred(int qtyTransferred) { this.qtyTransferred = qtyTransferred; }
    public Instant getTransferDate() { return transferDate; }
    public void setTransferDate(Instant transferDate) { this.transferDate = transferDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getTransferCreateAt() { return transferCreateAt; }
    public void setTransferCreateAt(Instant transferCreateAt) { this.transferCreateAt = transferCreateAt; }
    public Long getTransferCreateBy() { return transferCreateBy; }
    public void setTransferCreateBy(Long transferCreateBy) { this.transferCreateBy = transferCreateBy; }
    public Instant getTransferUpdateAt() { return transferUpdateAt; }
    public void setTransferUpdateAt(Instant transferUpdateAt) { this.transferUpdateAt = transferUpdateAt; }
    public Long getTransferUpdateBy() { return transferUpdateBy; }
    public void setTransferUpdateBy(Long transferUpdateBy) { this.transferUpdateBy = transferUpdateBy; }
}
