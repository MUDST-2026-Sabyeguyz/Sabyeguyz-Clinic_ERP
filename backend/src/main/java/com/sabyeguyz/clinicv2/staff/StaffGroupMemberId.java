package com.sabyeguyz.clinicv2.staff;

import java.io.Serializable;
import java.util.Objects;

public class StaffGroupMemberId implements Serializable {
    private Long staffGroup;
    private Long staff;

    public StaffGroupMemberId() {}

    public StaffGroupMemberId(
            Long staffGroup,
            Long staff) {
        this.staffGroup = staffGroup;
        this.staff = staff;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof StaffGroupMemberId that)) return false;

        return Objects.equals(staffGroup, that.staffGroup) && Objects.equals(staff, that.staff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staffGroup, staff);
    }
}
