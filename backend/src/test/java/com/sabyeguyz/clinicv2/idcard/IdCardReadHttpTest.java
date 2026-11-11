package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdCardReadHttpTest extends IdCardHttpTestSupport {
    static final String VALID_ID = "1101700005119";
    String agentToken;
    long workstationId;

    @BeforeEach
    void pairAWorkstation() {
        // no RESTART IDENTITY: workstation ids must not repeat, because the app keeps unclaimed reads in memory per workstation id
        db.execute("TRUNCATE TABLE id_card_read_audit, card_reader_workstation CASCADE");
        // Thai patients must have a national ID, so go back to the registered one (it is not the number on the test card)
        db.update("update patient set national_id = '1100000000001' where patient_id = ?", patientId);
        var created = reply("POST", "/api/id-card/workstations", Map.of("name", "เคาน์เตอร์ 1", "branchCode", "01")).body();
        workstationId = created.path("id").asLong();
        agentToken = replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", created.path("pairingCode").asText()))
                .body().path("token").asText();
        // the agent has polled once, so the workstation is online
        assertEquals(204, agent(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
    }

    Map<String, Object> card(String nationalId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("nationalId", nationalId);
        card.put("titleTh", "นาย");
        card.put("firstNameTh", "สมชาย");
        card.put("middleNameTh", null);
        card.put("lastNameTh", "ใจดี");
        card.put("fullNameEn", "Somchai Jaidee");
        card.put("birthDate", "1997-01-31");
        card.put("gender", "M");
        card.put("address", "88/8 หมู่ที่ 3 ตำบลจรเข้บัว อำเภอลาดพร้าว จังหวัดกรุงเทพมหานคร");
        card.put("issueDate", "2020-02-02");
        card.put("expiryDate", null);
        card.put("lifetime", true);
        return card;
    }

    /** A call made by the counter agent: its own token in X-Agent-Token, no user JWT. */
    HttpResult agent(String agentToken, String method, String path, Object payload) {
        return replyWithHeaders(null, method, path, payload, agentToken == null ? Map.of() : Map.of("X-Agent-Token", agentToken));
    }

    String start(String jwt) {
        HttpResult started = replyAs(jwt, "POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "REGISTER"));
        assertEquals(202, started.status(), started.body().toString());
        return started.body().path("id").asText();
    }

    String agentTakes() {
        HttpResult next = agent(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=2", null);
        assertEquals(200, next.status(), next.body().toString());
        return next.body().path("requestId").asText();
    }

    void agentSaysCardIsInTheReader(String requestId) {
        assertEquals(204, agent(agentToken, "POST", "/api/id-card/agent/progress",
                Map.of("requestId", requestId, "stage", "CARD_PRESENT")).status());
    }

    void agentAnswers(String requestId, String status, Map<String, Object> card) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("status", status);
        body.put("card", card);
        assertEquals(204, agent(agentToken, "POST", "/api/id-card/agent/result", body).status());
    }

    @Test
    void aCardOfSomeoneNewIsDeliveredOnceTogetherWithTheNotFoundLookup() {
        String id = start(token);
        assertEquals("REGISTER", agent(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=2", null).body().path("purpose").asText());
        assertEquals("PENDING", reply("GET", "/api/id-card/reads/" + id, null).body().path("status").asText());

        agentAnswers(id, "OK", card(VALID_ID));
        HttpResult done = reply("GET", "/api/id-card/reads/" + id, null);
        assertEquals("DONE", done.body().path("status").asText());
        assertFalse(done.body().path("lookup").path("exists").asBoolean());
        assertTrue(done.body().path("lookup").path("patient").isNull());
        assertEquals(VALID_ID, done.body().path("card").path("nationalId").asText());
        assertEquals("1997-01-31", done.body().path("card").path("birthDate").asText());

        assertEquals(404, reply("GET", "/api/id-card/reads/" + id, null).status(), "delivered once");
        assertEquals("SUCCESS", db.queryForObject("select outcome from id_card_read_audit", String.class));
    }

    @Test
    void aPatientWhoIsAlreadyRegisteredIsFoundAndNoCardDataLeavesTheServer() {
        db.update("update patient set national_id=? where patient_id=?", VALID_ID, patientId);
        String id = start(token);
        agentTakes();
        agentAnswers(id, "OK", card(VALID_ID));

        HttpResult done = reply("GET", "/api/id-card/reads/" + id, null);
        assertEquals("DONE", done.body().path("status").asText());
        assertTrue(done.body().path("lookup").path("exists").asBoolean());
        assertFalse(done.body().path("lookup").path("patient").path("hn").asText("").isBlank());
        assertFalse(done.body().path("lookup").path("patient").path("fullName").asText("").isBlank());
        String text = done.body().toString();
        assertFalse(done.body().has("card"));
        assertFalse(text.contains("1997-01-31") || text.contains(VALID_ID) || text.contains("ลาดพร้าว"), text);
    }

    @Test
    void theScreenSeesReadingOnceTheCardIsInTheReader() {
        String id = start(token);
        agentTakes();
        assertEquals("PENDING", reply("GET", "/api/id-card/reads/" + id, null).body().path("status").asText());

        agentSaysCardIsInTheReader(id);
        assertEquals("READING", reply("GET", "/api/id-card/reads/" + id, null).body().path("status").asText());
        assertEquals("READING", reply("GET", "/api/id-card/reads/" + id, null).body().path("status").asText(), "reading is not consumed");

        agentAnswers(id, "OK", card(VALID_ID));
        assertEquals("DONE", reply("GET", "/api/id-card/reads/" + id, null).body().path("status").asText());
    }

    @Test
    void progressForAnUnknownReadIsRefused() {
        assertEquals(404, agent(agentToken, "POST", "/api/id-card/agent/progress",
                Map.of("requestId", UUID.randomUUID().toString(), "stage", "CARD_PRESENT")).status());
    }

    @Test
    void aNumberWithAWrongCheckDigitIsRefused() {
        String id = start(token);
        agentTakes();
        agentAnswers(id, "OK", card("1101700005110"));
        HttpResult failed = reply("GET", "/api/id-card/reads/" + id, null);
        assertEquals("FAILED", failed.body().path("status").asText());
        assertEquals("INVALID_CARD", failed.body().path("outcome").asText());
        assertFalse(failed.body().has("card"));
        assertEquals("INVALID_CARD", db.queryForObject("select outcome from id_card_read_audit", String.class));
    }

    @Test
    void theAgentCanReportNoCardNoReaderAndErrors() {
        for (String outcome : new String[] {"NO_CARD", "NO_READER", "ERROR"}) {
            String id = start(token);
            agentTakes();
            agentAnswers(id, outcome, null);
            HttpResult failed = reply("GET", "/api/id-card/reads/" + id, null);
            assertEquals("FAILED", failed.body().path("status").asText());
            assertEquals(outcome, failed.body().path("outcome").asText());
        }
        assertEquals(3L, db.queryForObject("select count(*) from id_card_read_audit", Long.class));
    }

    @Test
    void onlyTheRegisterPurposeExists() {
        HttpResult started = reply("POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "LOOKUP"));
        assertEquals(400, started.status());
    }

    @Test
    void aWorkstationThatIsNotPollingIsOffline() {
        db.update("update card_reader_workstation set last_seen_at = now() - interval '5 minutes'");
        HttpResult started = reply("POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "REGISTER"));
        assertEquals(409, started.status());
    }

    @Test
    void anotherUsersReadIsInvisible() {
        String id = start(token);
        String therapist = login("therapist", "therapist123");
        assertEquals(404, replyAs(therapist, "GET", "/api/id-card/reads/" + id, null).status());
    }

    @Test
    void aUserOfAnotherBranchCannotUseTheWorkstation() {
        String otherBranch = login("therapist2", "therapist123");
        HttpResult started = replyAs(otherBranch, "POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "REGISTER"));
        assertEquals(403, started.status());
    }

    @Test
    void aRevokedWorkstationsTokenStopsWorking() {
        assertEquals(204, reply("POST", "/api/id-card/workstations/" + workstationId + "/revoke", null).status());
        assertEquals(401, agent(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
        assertEquals(401, agent(agentToken, "POST", "/api/id-card/agent/progress", Map.of("requestId", "x", "stage", "CARD_PRESENT")).status());
        assertEquals(401, agent("not-a-token", "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
        assertEquals(401, agent(null, "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
    }

    @Test
    void tooManyStartsInAMinuteAreRefused() {
        String therapist = login("therapist", "therapist123"); // a user whose allowance no other test has used
        int accepted = 0;
        int refused = 0;
        for (int i = 0; i < 22; i++) {
            int status = replyAs(therapist, "POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "REGISTER")).status();
            if (status == 202) accepted++;
            if (status == 429) refused++;
        }
        assertEquals(20, accepted);
        assertEquals(2, refused);
    }

    @Test
    void auditRecordsTheReadWithoutAnyCardData() {
        String id = start(token);
        agentTakes();
        agentAnswers(id, "OK", card(VALID_ID));
        var row = db.queryForMap("select workstation_id, user_id, branch_id, purpose, outcome from id_card_read_audit");
        assertEquals(workstationId, ((Number) row.get("workstation_id")).longValue());
        assertEquals(actorId, ((Number) row.get("user_id")).longValue());
        assertEquals(branchId, ((Number) row.get("branch_id")).longValue());
        assertEquals("REGISTER", row.get("purpose"));
        assertEquals("SUCCESS", row.get("outcome"));
    }
}
