package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdCardWorkstationHttpTest extends IdCardHttpTestSupport {
    @BeforeEach
    void cleanWorkstations() {
        db.execute("TRUNCATE TABLE id_card_read_audit, card_reader_workstation RESTART IDENTITY CASCADE");
    }

    private HttpResult createWorkstation(String name, String branchCode) {
        return reply("POST", "/api/id-card/workstations", Map.of("name", name, "branchCode", branchCode));
    }

    @Test
    void managerCreatesAWorkstationAndGetsAOneTimePairingCode() {
        HttpResult created = createWorkstation("เคาน์เตอร์ 1", "01");
        assertEquals(200, created.status(), created.body().toString());
        assertEquals(8, created.body().path("pairingCode").asText().length());
        assertEquals("01", created.body().path("branchCode").asText());

        HttpResult list = reply("GET", "/api/id-card/workstations", null);
        assertEquals(200, list.status());
        assertEquals("เคาน์เตอร์ 1", list.body().get(0).path("name").asText());
        assertFalse(list.body().get(0).path("online").asBoolean());
        assertFalse(list.body().get(0).has("pairingCode"), "the code is shown only when it is created");
    }

    @Test
    void pairingGivesATokenOnceAndStoresOnlyItsHash() {
        String code = createWorkstation("เคาน์เตอร์ 1", "01").body().path("pairingCode").asText();

        HttpResult paired = replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", code));
        assertEquals(200, paired.status(), paired.body().toString());
        String token = paired.body().path("token").asText();
        assertTrue(token.length() >= 32);
        assertEquals("01", paired.body().path("branchCode").asText());

        String stored = db.queryForObject("select token_hash from card_reader_workstation", String.class);
        assertEquals(64, stored.trim().length());
        assertNotEquals(token, stored.trim());
        assertNull(db.queryForObject("select pairing_code_hash from card_reader_workstation", String.class),
                "the pairing code is consumed");

        assertEquals(400, replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", code)).status());
    }

    @Test
    void anExpiredOrUnknownPairingCodeIsRefused() {
        String code = createWorkstation("เคาน์เตอร์ 1", "01").body().path("pairingCode").asText();
        db.update("update card_reader_workstation set pairing_expires_at = now() - interval '1 minute'");
        assertEquals(400, replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", code)).status());
        assertEquals(400, replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", "ZZZZZZZZ")).status());
    }

    @Test
    void aNewPairingCodeReplacesTheOldOne() {
        HttpResult created = createWorkstation("เคาน์เตอร์ 1", "01");
        long id = created.body().path("id").asLong();
        String old = created.body().path("pairingCode").asText();
        String fresh = reply("POST", "/api/id-card/workstations/" + id + "/pairing-code", null).body().path("pairingCode").asText();
        assertNotEquals(old, fresh);
        assertEquals(400, replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", old)).status());
        assertEquals(200, replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", fresh)).status());
    }

    @Test
    void aTherapistCannotManageWorkstations() {
        String therapist = login("therapist", "therapist123");
        assertEquals(403, replyAs(therapist, "POST", "/api/id-card/workstations", Map.of("name", "x", "branchCode", "01")).status());
        assertEquals(401, replyAs(null, "GET", "/api/id-card/workstations", null).status());
    }

    @Test
    void aTherapistSeesOnlyTheWorkstationsOfTheirOwnBranch() {
        createWorkstation("สาขา 1", "01");
        createWorkstation("สาขา 2", "02");
        HttpResult list = replyAs(login("therapist", "therapist123"), "GET", "/api/id-card/workstations", null);
        assertEquals(200, list.status());
        assertEquals(1, list.body().size());
        assertEquals("01", list.body().get(0).path("branchCode").asText());
    }
}
