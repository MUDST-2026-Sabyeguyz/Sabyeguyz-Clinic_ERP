package com.sabyeguyz.clinicv2.course;

import java.time.Instant;

import jakarta.persistence.*;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.patient.Patient;

/**
 * Per-member session balance of a course (cache of Course_ledger; the ledger
 * stays authoritative). A row must correspond to a Course_shared_member row.
 */
@Entity
@Table(name = "course_entitlement")
@IdClass(CourseEntitlementId.class)
public class CourseEntitlement {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "allocated_visits", nullable = false)
    private int allocatedVisits;

    @Column(name = "used_visits", nullable = false)
    private int usedVisits;

    @Column(name = "remaining_visits", nullable = false)
    private int remainingVisits;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CourseEntitlement() {}

    public CourseEntitlement(
            Course course,
            Patient patient,
            int allocatedVisits) {
        this.course = course;
        this.patient = patient;
        this.allocatedVisits = allocatedVisits;
        this.usedVisits = 0;
        this.remainingVisits = allocatedVisits;
        this.updatedAt = Instant.now();
    }

    /** Sessions given to (or taken back from) this member; remaining moves with it. */
    public void adjustAllocated(int delta) {
        allocatedVisits += delta;
        remainingVisits += delta;
        updatedAt = Instant.now();
    }

    /** Sessions used (+) or restored (-) by this member. */
    public void adjustUsed(int delta) {
        usedVisits += delta;
        remainingVisits -= delta;
        updatedAt = Instant.now();
    }

    public Course getCourse() { return course; }

    public Patient getPatient() { return patient; }

    public int getAllocatedVisits() { return allocatedVisits; }

    public int getUsedVisits() { return usedVisits; }

    public int getRemainingVisits() { return remainingVisits; }

    public Instant getUpdatedAt() { return updatedAt; }
}
