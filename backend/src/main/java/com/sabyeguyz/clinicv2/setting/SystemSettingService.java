package com.sabyeguyz.clinicv2.setting;

import com.sabyeguyz.clinicv2.role.Permission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-editable system settings (e.g. TREATMENT_FEE_OVERFLOW_POLICY),
 * per Appendix B of Commission_System_Requirement_Dev. Nothing in this
 * codebase reads these yet — see the "hard-coded to CAP_AT_COMMISSION"
 * note on CourseVisitUsageService for the concrete gap this settings
 * table is meant to close.
 */
@Service
public class SystemSettingService {
    private final SystemSettingRepository repository;

    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<SystemSetting> get(String key) {
        return repository.findById(key);
    }

    @Transactional
    public SystemSetting set(String settingKey, Permission permission, String settingValue, String description,
                              Long actorId) {
        String key = requireText(settingKey, "settingKey");
        SystemSetting setting = repository.findById(key)
                .orElseGet(() -> new SystemSetting(key, permission, requireText(settingValue, "settingValue")));
        setting.setPermission(permission);
        setting.setSettingValue(requireText(settingValue, "settingValue"));
        setting.setDescription(description);
        setting.setSettingUpdateAt(Instant.now());
        setting.setSettingUpdateBy(actorId);
        return repository.save(setting);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
