package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CourseMemberRole;
import com.sabyeguyz.clinicv2.patient.Patient;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "course_shared_member")
@IdClass(CourseSharedMemberId.class)
public class CourseSharedMember {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "member_role", nullable = false, columnDefinition = "course_member_role_enum")
    private CourseMemberRole memberRole;

    @Column(name = "member_status", nullable = false)
    private boolean memberStatus = true;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    public CourseSharedMember() {}

    public CourseSharedMember(Course course, Patient patient, CourseMemberRole memberRole) {
        this.course = course;
        this.patient = patient;
        this.memberRole = memberRole;
        this.memberStatus = true;
    }

    public Course getCourse() { return course; }
    public Patient getPatient() { return patient; }
    public CourseMemberRole getMemberRole() { return memberRole; }
    public void setMemberRole(CourseMemberRole memberRole) { this.memberRole = memberRole; }
    public boolean isMemberStatus() { return memberStatus; }
    public void setMemberStatus(boolean memberStatus) { this.memberStatus = memberStatus; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
