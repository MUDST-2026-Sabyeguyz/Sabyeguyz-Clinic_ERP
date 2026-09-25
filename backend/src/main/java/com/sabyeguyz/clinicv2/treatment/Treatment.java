package com.sabyeguyz.clinicv2.treatment;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "treatment")
public class Treatment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "treatment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private PatientVisit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private Service service;

    /** Treating PT (who performs the service). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treating_staff_id")
    private Staff treatingStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_transaction_id")
    private SalesTransaction salesTransaction;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "treatment_create_by", nullable = false, updatable = false)
    private Long treatmentCreateBy;

    @Column(name = "treatment_create_at", nullable = false, updatable = false)
    private Instant treatmentCreateAt;

    @Column(name = "treatment_update_at")
    private Instant treatmentUpdateAt;

    @Column(name = "treatment_update_by")
    private Long treatmentUpdateBy;

    protected Treatment() {}

    public Long getId() { return id; }
    public PatientVisit getVisit() { return visit; }
    public void setVisit(PatientVisit visit) { this.visit = visit; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public Staff getTreatingStaff() { return treatingStaff; }
    public void setTreatingStaff(Staff treatingStaff) { this.treatingStaff = treatingStaff; }
    public SalesTransaction getSalesTransaction() { return salesTransaction; }
    public void setSalesTransaction(SalesTransaction salesTransaction) { this.salesTransaction = salesTransaction; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getTreatmentCreateBy() { return treatmentCreateBy; }
    public void setTreatmentCreateBy(Long treatmentCreateBy) { this.treatmentCreateBy = treatmentCreateBy; }
    public Instant getTreatmentCreateAt() { return treatmentCreateAt; }
    public void setTreatmentCreateAt(Instant treatmentCreateAt) { this.treatmentCreateAt = treatmentCreateAt; }
    public Instant getTreatmentUpdateAt() { return treatmentUpdateAt; }
    public void setTreatmentUpdateAt(Instant treatmentUpdateAt) { this.treatmentUpdateAt = treatmentUpdateAt; }
    public Long getTreatmentUpdateBy() { return treatmentUpdateBy; }
    public void setTreatmentUpdateBy(Long treatmentUpdateBy) { this.treatmentUpdateBy = treatmentUpdateBy; }
}
