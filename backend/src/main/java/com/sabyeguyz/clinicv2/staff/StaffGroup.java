package com.sabyeguyz.clinicv2.staff;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "staff_group")
public class StaffGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_group_id")
    private Long id;

    @Column(name = "group_name", length = 100, nullable = false)
    private String groupName;

    @Column(name = "staff_group_create_at", nullable = false, updatable = false)
    private Instant staffGroupCreateAt;

    @Column(name = "staff_group_create_by", nullable = false, updatable = false)
    private Long staffGroupCreateBy;

    @Column(name = "staff_group_update_at")
    private Instant staffGroupUpdateAt;

    @Column(name = "staff_group_update_by")
    private Long staffGroupUpdateBy;

    public StaffGroup() {}

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Instant getStaffGroupCreateAt() { return staffGroupCreateAt; }
    public void setStaffGroupCreateAt(Instant staffGroupCreateAt) { this.staffGroupCreateAt = staffGroupCreateAt; }
    public Long getStaffGroupCreateBy() { return staffGroupCreateBy; }
    public void setStaffGroupCreateBy(Long staffGroupCreateBy) { this.staffGroupCreateBy = staffGroupCreateBy; }
    public Instant getStaffGroupUpdateAt() { return staffGroupUpdateAt; }
    public void setStaffGroupUpdateAt(Instant staffGroupUpdateAt) { this.staffGroupUpdateAt = staffGroupUpdateAt; }
    public Long getStaffGroupUpdateBy() { return staffGroupUpdateBy; }
    public void setStaffGroupUpdateBy(Long staffGroupUpdateBy) { this.staffGroupUpdateBy = staffGroupUpdateBy; }
}
