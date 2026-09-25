package com.sabyeguyz.clinicv2.staff;

import jakarta.persistence.*;

@Entity
@Table(name = "staff_group_member")
@IdClass(StaffGroupMemberId.class)
public class StaffGroupMember {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_group_id")
    private StaffGroup staffGroup;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    protected StaffGroupMember() {}

    public StaffGroupMember(StaffGroup staffGroup, Staff staff) {
        this.staffGroup = staffGroup;
        this.staff = staff;
    }

    public StaffGroup getStaffGroup() { return staffGroup; }
    public Staff getStaff() { return staff; }
}
