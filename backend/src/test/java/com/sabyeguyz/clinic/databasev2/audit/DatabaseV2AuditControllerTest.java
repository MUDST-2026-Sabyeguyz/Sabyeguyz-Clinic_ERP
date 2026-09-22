package com.sabyeguyz.clinic.databasev2.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DatabaseV2AuditControllerTest {

    private DatabaseV2AuditRepository auditRepository;
    private DatabaseV2AuditController controller;

    @BeforeEach
    void setUp() {
        auditRepository = mock(DatabaseV2AuditRepository.class);
        controller = new DatabaseV2AuditController(auditRepository);
    }

    @Test
    void listReturnsPagedAuditLogs() {
        DatabaseV2AuditLogDto dto = DatabaseV2AuditLogDto.of(
                1L, "course", "10", "INSERT", null, "{'status':'Active'}", 1L, Instant.now()
        );
        Page<DatabaseV2AuditLogDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 50), 1);
        when(auditRepository.list(0, 50)).thenReturn(page);

        Page<DatabaseV2AuditLogDto> result = controller.list(0, 50);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).auditId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        assertThat(result.getContent().get(0).tableName()).isEqualTo("course");
        assertThat(result.getContent().get(0).action()).isEqualTo("INSERT");
    }

    @Test
    void listValidatesPageAndSize() {
        assertThatThrownBy(() -> controller.list(-1, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid page or size");

        assertThatThrownBy(() -> controller.list(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid page or size");

        assertThatThrownBy(() -> controller.list(0, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid page or size");
    }
}
