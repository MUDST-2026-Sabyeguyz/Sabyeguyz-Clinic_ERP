package com.sabyeguyz.clinicv2.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.enums.AuditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock AuditLogRepository repository;

    private AuditLogService service() {
        return new AuditLogService(repository);
    }

    @Test
    void recordsAnAuditEntry() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuditLog log = service().record("branch", "1", AuditAction.UPDATE, "{\"phone\":\"080\"}",
                "{\"phone\":\"081\"}", 1L);

        assertThat(log.getTableName()).isEqualTo("branch");
        assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE);
    }

    @Test
    void rejectsMissingAction() {
        assertThatThrownBy(() -> service().record("branch", "1", null, null, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
