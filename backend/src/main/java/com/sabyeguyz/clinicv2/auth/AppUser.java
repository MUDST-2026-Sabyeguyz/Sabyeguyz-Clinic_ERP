package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(length = 50, nullable = false, unique = true)
    private String username;

    @Column(length = 254)
    private String email;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "user_status", nullable = false)
    private boolean userStatus = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "user_create_at", nullable = false, updatable = false)
    private Instant userCreateAt;

    @Column(name = "user_create_by", updatable = false)
    private Long userCreateBy;

    @Column(name = "user_update_at")
    private Instant userUpdateAt;

    @Column(name = "user_update_by")
    private Long userUpdateBy;

    public AppUser() {}

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public boolean isUserStatus() { return userStatus; }
    public void setUserStatus(boolean userStatus) { this.userStatus = userStatus; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getUserCreateAt() { return userCreateAt; }
    public void setUserCreateAt(Instant userCreateAt) { this.userCreateAt = userCreateAt; }
    public Long getUserCreateBy() { return userCreateBy; }
    public void setUserCreateBy(Long userCreateBy) { this.userCreateBy = userCreateBy; }
    public Instant getUserUpdateAt() { return userUpdateAt; }
    public void setUserUpdateAt(Instant userUpdateAt) { this.userUpdateAt = userUpdateAt; }
    public Long getUserUpdateBy() { return userUpdateBy; }
    public void setUserUpdateBy(Long userUpdateBy) { this.userUpdateBy = userUpdateBy; }
}
