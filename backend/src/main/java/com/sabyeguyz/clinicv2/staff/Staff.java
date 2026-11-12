package com.sabyeguyz.clinicv2.staff;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.StaffPosition;

@Entity
@Table(name = "staff")
public class Staff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Long id;

    @Column(name = "staff_code", length = 20, nullable = false, unique = true)
    private String staffCode;

    @Column(name = "staff_fname", length = 100, nullable = false)
    private String staffFname;

    @Column(name = "staff_lname", length = 100, nullable = false)
    private String staffLname;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "position_enum")
    private StaffPosition position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "staff_status", nullable = false)
    private boolean staffStatus = true;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "staff_create_at", nullable = false, updatable = false)
    private Instant staffCreateAt;

    @Column(name = "staff_create_by", nullable = false, updatable = false)
    private Long staffCreateBy;

    @Column(name = "staff_update_at")
    private Instant staffUpdateAt;

    @Column(name = "staff_update_by")
    private Long staffUpdateBy;

    public Staff() {}

    public Long getId() { return id; }

    public String getStaffCode() { return staffCode; }
    public void setStaffCode(String staffCode) { this.staffCode = staffCode; }

    public String getStaffFname() { return staffFname; }
    public void setStaffFname(String staffFname) { this.staffFname = staffFname; }

    public String getStaffLname() { return staffLname; }
    public void setStaffLname(String staffLname) { this.staffLname = staffLname; }

    public StaffPosition getPosition() { return position; }
    public void setPosition(StaffPosition position) { this.position = position; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public boolean isStaffStatus() { return staffStatus; }
    public void setStaffStatus(boolean staffStatus) { this.staffStatus = staffStatus; }

    public LocalDate getTerminationDate() { return terminationDate; }
    public void setTerminationDate(LocalDate terminationDate) { this.terminationDate = terminationDate; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public Instant getStaffCreateAt() { return staffCreateAt; }
    public void setStaffCreateAt(Instant staffCreateAt) { this.staffCreateAt = staffCreateAt; }

    public Long getStaffCreateBy() { return staffCreateBy; }
    public void setStaffCreateBy(Long staffCreateBy) { this.staffCreateBy = staffCreateBy; }

    public Instant getStaffUpdateAt() { return staffUpdateAt; }
    public void setStaffUpdateAt(Instant staffUpdateAt) { this.staffUpdateAt = staffUpdateAt; }

    public Long getStaffUpdateBy() { return staffUpdateBy; }
    public void setStaffUpdateBy(Long staffUpdateBy) { this.staffUpdateBy = staffUpdateBy; }
}
