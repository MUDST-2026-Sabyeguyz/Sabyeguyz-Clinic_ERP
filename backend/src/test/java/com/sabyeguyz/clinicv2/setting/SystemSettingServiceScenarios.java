package com.sabyeguyz.clinicv2.setting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.role.Permission;

public class SystemSettingServiceScenarios {
    V2RepositoryStub<SystemSettingRepository> repositoryStub = new V2RepositoryStub<>(SystemSettingRepository.class);
    SystemSettingRepository repository = repositoryStub.proxy();

    private SystemSettingService service() {
        return new SystemSettingService(repository);
    }

    public Map<String, Object> createsANewSetting() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findById", args -> Optional.empty());

        SystemSetting setting = service().set(
                "TREATMENT_FEE_OVERFLOW_POLICY",
                new Permission(),
                "CAP_AT_COMMISSION",
                "นโยบายเมื่อค่ามือเกิน",
                1L);

        out.put("settingKey", setting.getSettingKey());
        out.put("settingValue", setting.getSettingValue());
        return out;
    }

    public Map<String, Object> updatesAnExistingSettingInPlace() {
        Map<String, Object> out = new LinkedHashMap<>();
        SystemSetting existing = new SystemSetting(
                "TREATMENT_FEE_OVERFLOW_POLICY",
                new Permission(),
                "COMPANY_TOP_UP");
        repositoryStub.on("findById", args -> Optional.of(existing));

        SystemSetting updated = service().set(
                "TREATMENT_FEE_OVERFLOW_POLICY",
                new Permission(),
                "CAP_AT_COMMISSION",
                null,
                1L);

        out.put("sameSettingInstance", updated == existing);
        out.put("settingValue", updated.getSettingValue());
        return out;
    }

    public Map<String, Object> rejectsBlankValue() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findById", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> {
            service().set("X", new Permission(), "  ", null, 1L);
            return null;
        }));
        return out;
    }
}
