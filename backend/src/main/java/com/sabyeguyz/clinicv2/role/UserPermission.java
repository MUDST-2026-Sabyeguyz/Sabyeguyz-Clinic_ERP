package com.sabyeguyz.clinicv2.role;

import com.sabyeguyz.clinicv2.auth.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** สิทธิ์เฉพาะผู้ใช้ที่เพิ่มหรือยกเว้นจากสิทธิ์ตาม role */
@Entity
@Table(name = "user_permission")
@IdClass(UserPermissionId.class)
public class UserPermission {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "is_allowed", nullable = false)
    private boolean allowed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private AppUser updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserPermission() {}

    public UserPermission(AppUser user, Permission permission, boolean allowed, AppUser updatedBy,
                          LocalDateTime updatedAt) {
        this.user = user;
        this.permission = permission;
        this.allowed = allowed;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public AppUser getUser() { return user; }
    public Permission getPermission() { return permission; }
    public boolean isAllowed() { return allowed; }
    public AppUser getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public void setUpdatedBy(AppUser updatedBy) { this.updatedBy = updatedBy; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
