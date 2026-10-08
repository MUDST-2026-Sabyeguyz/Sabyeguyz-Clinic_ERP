package com.sabyeguyz.clinicv2.course;

import java.io.Serializable;
import java.util.Objects;

public class CourseSaleStaffShareId implements Serializable {
    private Long course;
    private Long staff;

    public CourseSaleStaffShareId() {}

    public CourseSaleStaffShareId(Long course, Long staff) {
        this.course = course;
        this.staff = staff;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CourseSaleStaffShareId that)) return false;
        return Objects.equals(course, that.course) && Objects.equals(staff, that.staff);
    }

    @Override
    public int hashCode() { return Objects.hash(course, staff); }
}
