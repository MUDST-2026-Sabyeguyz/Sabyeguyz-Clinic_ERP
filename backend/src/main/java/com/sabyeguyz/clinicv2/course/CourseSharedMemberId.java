package com.sabyeguyz.clinicv2.course;

import java.io.Serializable;
import java.util.Objects;

public class CourseSharedMemberId implements Serializable {
    private Long course;
    private Long patient;

    public CourseSharedMemberId() {}

    public CourseSharedMemberId(
            Long course,
            Long patient) {
        this.course = course;
        this.patient = patient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof CourseSharedMemberId that)) return false;

        return Objects.equals(course, that.course) && Objects.equals(patient, that.patient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(course, patient);
    }
}
