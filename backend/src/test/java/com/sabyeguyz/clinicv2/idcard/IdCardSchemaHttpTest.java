package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import com.sabyeguyz.clinicv2.branch.BranchRepository;
import org.junit.jupiter.api.Test;

class IdCardSchemaHttpTest extends IdCardHttpTestSupport {
    @Test
    void tablesExistAndAWorkstationCanBeSaved() {
        assertEquals(0L, db.queryForObject("select count(*) from card_reader_workstation", Long.class));
        assertEquals(0L, db.queryForObject("select count(*) from id_card_read_audit", Long.class));

        CardReaderWorkstation workstation = new CardReaderWorkstation();
        workstation.setName("เคาน์เตอร์ 1");
        workstation.setBranch(context.getBean(BranchRepository.class).findByBranchCode("01").orElseThrow());
        CardReaderWorkstation saved = context.getBean(CardReaderWorkstationRepository.class).save(workstation);

        assertNotNull(saved.getId());
        assertEquals("Active", saved.getStatus());
        assertEquals(1L, db.queryForObject("select count(*) from card_reader_workstation", Long.class));
    }

    @Test
    void auditTableHasNoCardDataColumn() {
        var columns = db.queryForList("select column_name from information_schema.columns where table_name='id_card_read_audit' order by ordinal_position", String.class);
        assertEquals(java.util.List.of("audit_id", "workstation_id", "user_id", "branch_id", "purpose", "outcome", "created_at"), columns);
    }
}
