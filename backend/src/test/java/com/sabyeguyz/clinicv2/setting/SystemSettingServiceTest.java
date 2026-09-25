package com.sabyeguyz.clinicv2.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.role.Permission;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {
    @Mock SystemSettingRepository repository;

    private SystemSettingService service() {
        return new SystemSettingService(repository);
    }

    @Test
    void createsANewSetting() {
        when(repository.findById("TREATMENT_FEE_OVERFLOW_POLICY")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        SystemSetting setting = service().set("TREATMENT_FEE_OVERFLOW_POLICY", new Permission(),
                "CAP_AT_COMMISSION", "นโยบายเมื่อค่ามือเกิน", 1L);

        assertThat(setting.getSettingKey()).isEqualTo("TREATMENT_FEE_OVERFLOW_POLICY");
        assertThat(setting.getSettingValue()).isEqualTo("CAP_AT_COMMISSION");
    }

    @Test
    void updatesAnExistingSettingInPlace() {
        SystemSetting existing = new SystemSetting("TREATMENT_FEE_OVERFLOW_POLICY", new Permission(), "COMPANY_TOP_UP");
        when(repository.findById("TREATMENT_FEE_OVERFLOW_POLICY")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        SystemSetting updated = service().set("TREATMENT_FEE_OVERFLOW_POLICY", new Permission(),
                "CAP_AT_COMMISSION", null, 1L);

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getSettingValue()).isEqualTo("CAP_AT_COMMISSION");
    }

    @Test
    void rejectsBlankValue() {
        when(repository.findById("X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().set("X", new Permission(), "  ", null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
