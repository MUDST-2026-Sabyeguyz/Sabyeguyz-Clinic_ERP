package com.sabyeguyz.clinicv2.course;

import java.time.Instant;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.catalog.Course;

/** One version of the printable document for a course usage; document_snapshot freezes what was printed. */
@Entity
@Table(name = "course_usage_print")
public class CourseUsagePrint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_usage_id")
    private CourseVisitUsage visitUsage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "document_version", nullable = false)
    private int documentVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_snapshot", nullable = false)
    private String documentSnapshot;

    public CourseUsagePrint() {}

    public Long getId() { return id; }

    public CourseVisitUsage getVisitUsage() { return visitUsage; }
    public void setVisitUsage(CourseVisitUsage visitUsage) { this.visitUsage = visitUsage; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long issuedBy) { this.issuedBy = issuedBy; }

    public int getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(int documentVersion) { this.documentVersion = documentVersion; }

    public String getDocumentSnapshot() { return documentSnapshot; }
    public void setDocumentSnapshot(String documentSnapshot) { this.documentSnapshot = documentSnapshot; }
}
