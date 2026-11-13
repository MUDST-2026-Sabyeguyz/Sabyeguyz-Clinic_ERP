package com.sabyeguyz.clinicv2.setting;

import java.time.Instant;

import jakarta.persistence.*;

import com.sabyeguyz.clinicv2.role.Permission;

/** e.g. TREATMENT_FEE_OVERFLOW_POLICY. */
@Entity
@Table(name = "system_setting")
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 50)
    private String settingKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Column(name = "setting_value", length = 255, nullable = false)
    private String settingValue;

    @Column(length = 255)
    private String description;

    @Column(name = "setting_update_at")
    private Instant settingUpdateAt;

    @Column(name = "setting_update_by")
    private Long settingUpdateBy;

    public SystemSetting() {}

    public SystemSetting(
            String settingKey,
            Permission permission,
            String settingValue) {
        this.settingKey = settingKey;
        this.permission = permission;
        this.settingValue = settingValue;
    }

    public String getSettingKey() { return settingKey; }

    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }

    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getSettingUpdateAt() { return settingUpdateAt; }
    public void setSettingUpdateAt(Instant settingUpdateAt) { this.settingUpdateAt = settingUpdateAt; }

    public Long getSettingUpdateBy() { return settingUpdateBy; }
    public void setSettingUpdateBy(Long settingUpdateBy) { this.settingUpdateBy = settingUpdateBy; }
}
