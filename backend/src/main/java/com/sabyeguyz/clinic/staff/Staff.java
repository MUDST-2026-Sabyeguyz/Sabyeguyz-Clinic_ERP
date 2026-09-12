package com.sabyeguyz.clinic.staff;

import com.sabyeguyz.clinic.auth.AppUser;
import com.sabyeguyz.clinic.branch.Branch;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "staff")
public class Staff {
    @Id private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private AppUser user;

    @Column(name = "employee_code")
    private String employeeCode;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Staff() {}

    public Staff(AppUser u, String code, String name, String phone) {
        user = u;
        employeeCode = code;
        fullName = name;
        this.phone = phone;
    }

    public void assignBranch(Branch value) {
        branch = value;
    }

    public void update(String code, String name, String phone) {
        employeeCode = code;
        fullName = name;
        this.phone = phone;
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Branch getBranch() {
        return branch;
    }

    public boolean isActive() {
        return active;
    }
}
