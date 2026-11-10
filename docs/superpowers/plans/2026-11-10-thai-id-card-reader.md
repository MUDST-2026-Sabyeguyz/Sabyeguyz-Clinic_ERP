# Thai ID Card Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let counter staff insert a Thai national ID card into a USB reader and (a) autofill the new-patient registration form or (b) find the existing patient, on Windows/macOS/Linux with Chrome, Edge, Firefox and Safari.

**Architecture:** A small Java agent runs on the counter PC, reads the card over PC/SC and talks to the backend over HTTPS long-poll with a per-workstation token. The browser never talks to the reader: it asks the backend to start a read, then polls the backend for the one-time result. A LOOKUP read is resolved on the server so the browser never receives the card's personal data.

**Tech Stack:** Spring Boot 3.4.5 / Java 17 / JPA / Flyway / PostgreSQL (backend); Java 17 + `javax.smartcardio` + `java.net.http` + Jackson + jpackage (agent); Vue 3 + TypeScript + vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-11-10-thai-id-card-reader-design.md`

## Global Constraints

- Backend and agent target **Java 17** (`java.version` 17 in `backend/pom.xml`). **No new Spring dependency** in the backend (no WebSocket starter).
- Agent ↔ backend is **HTTPS long-poll** (`GET /api/id-card/agent/next`, held at most 25 s); browser ↔ backend is **polling** (`GET /api/id-card/reads/{id}`, every 700 ms). No WebSocket, no SSE.
- A read result lives **60 s in memory**, is delivered **once** to the user who started it, then deleted. Card data is **never** written to a log, the database or the agent's disk during a read.
- The audit table `id_card_read_audit` has **no card-data column**.
- **No photo** is read or stored. Identity verification at check-in/payment is out of scope.
- Checksum validation applies **only to card-read results**. Do not change `PatientService` national-ID validation (existing demo data and tests use IDs that are not checksum-valid).
- A manager creates workstations; pairing codes are single use and expire after 10 minutes; tokens are stored only as SHA-256 hex.
- UI text is Thai. Staff may always type the data by hand (the reader is optional).
- Commit messages carry **no AI attribution lines** (project rule). Do not push; pushing is the user's decision.
- Backend tests run through Docker (host JVM cannot open a NIO selector): use the existing Testcontainers-based HTTP tests; run them as described in each task's "Run" line.
- Branches: backend + agent on `feat/id-card-reader-backend` created from `origin/feat/database`; frontend on `feat/id-card-reader-frontend` created from the cleaned `fix-frontend` (`8cd06e5` at the time of writing, i.e. local `fix-frontend-clean`).

## File Structure

Backend (`backend/src/main/java/com/sabyeguyz/clinicv2/idcard/`):

| File | Responsibility |
|---|---|
| `ThaiNationalId.java` | check-digit validation |
| `CardData.java` | card payload record (agent → backend → browser) |
| `CardReaderWorkstation.java`, `CardReaderWorkstationRepository.java` | workstation entity/repo |
| `IdCardReadAudit.java`, `IdCardReadAuditRepository.java` | audit entity/repo (no card data) |
| `TokenHasher.java` | SHA-256 hex |
| `WorkstationService.java` | create / pair / authenticate / revoke / touch |
| `ReadRequestStore.java` | in-memory requests, per-workstation queue, one-time claim |
| `ReadRateLimiter.java` | max 20 starts per user per minute |
| `IdCardService.java` | start / next / agentResult / poll orchestration |
| `WorkstationController.java` | manager CRUD + staff list |
| `IdCardReadController.java` | staff start/poll |
| `IdCardAgentController.java` | agent pair/next/result |

Backend resources/tests: `db/migration/V1023__id_card_reader.sql`; tests under `backend/src/test/java/com/sabyeguyz/clinicv2/idcard/` (plain JUnit) and `backend/src/test/java/com/sabyeguyz/schemacheck/` (HTTP tests extending `CoursePaymentHttpTestSupport`, which is package-private in that package).

Agent (`id-card-agent/`, Maven, package `com.sabyeguyz.idcard.agent`): `CardData`, `ThaiIdCardParser`, `ApduChannel`, `ThaiIdCardReader`, `CardSource`/`PcscCardSource`, `AgentConfig`, `BackendClient`, `AgentLoop`, `Autostart`, `StatusTray`, `AgentMain`, `packaging/`.

Frontend (`frontend/src/`): `api/client.ts` (modify), `features/idcard/{cardToForm.ts,useIdCardReader.ts,IdCardReadButton.vue,CardReadersView.vue}` + specs, `features/patients/views/PatientRegistrationView.vue` (modify), `features/patients/views/PatientSearchView.vue` (modify), `router/index.ts` (modify).

---

## Part A — Backend

### Task 1: Thai national ID check digit

**Files:**
- Create: `backend/src/main/java/com/sabyeguyz/clinicv2/idcard/ThaiNationalId.java`
- Test: `backend/src/test/java/com/sabyeguyz/clinicv2/idcard/ThaiNationalIdTest.java`

**Interfaces:**
- Produces: `ThaiNationalId.isValid(String): boolean`

- [ ] **Step 1: Write the failing test**

```java
package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThaiNationalIdTest {
    @Test
    void acceptsANumberWithAMatchingCheckDigit() {
        // 1*13+1*12+0*11+1*10+7*9+0*8+0*7+0*6+0*5+5*4+1*3+1*2 = 123; 123 % 11 = 2; (11 - 2) % 10 = 9
        assertTrue(ThaiNationalId.isValid("1101700005119"));
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertFalse(ThaiNationalId.isValid("1101700005110"));
    }

    @Test
    void rejectsNullShortLongAndNonDigits() {
        assertFalse(ThaiNationalId.isValid(null));
        assertFalse(ThaiNationalId.isValid("123"));
        assertFalse(ThaiNationalId.isValid("11017000051190"));
        assertFalse(ThaiNationalId.isValid("110170000511A"));
        assertFalse(ThaiNationalId.isValid("0000000000000"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run (repo root, Docker Maven as in the project's test setup): `docker run --rm -v "$PWD/backend":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q -Dtest=ThaiNationalIdTest test`
Expected: FAIL — `ThaiNationalId` does not exist (compilation error).

- [ ] **Step 3: Implement**

```java
package com.sabyeguyz.clinicv2.idcard;

/** Check digit of a Thai national ID number: 13 digits, weights 13..2, mod 11. */
public final class ThaiNationalId {
    private ThaiNationalId() {}

    public static boolean isValid(String value) {
        if (value == null || !value.matches("[0-9]{13}")) return false;
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (value.charAt(i) - '0') * (13 - i);
        return (11 - sum % 11) % 10 == value.charAt(12) - '0';
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: same command. Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/sabyeguyz/clinicv2/idcard/ThaiNationalId.java backend/src/test/java/com/sabyeguyz/clinicv2/idcard/ThaiNationalIdTest.java
git commit -m "feat(idcard): check digit of a Thai national ID number"
```

---

### Task 2: Schema, entities and repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V1023__id_card_reader.sql` (first confirm no newer `V10xx` exists on the base branch; use the next free number)
- Create: `.../idcard/CardReaderWorkstation.java`, `CardReaderWorkstationRepository.java`, `IdCardReadAudit.java`, `IdCardReadAuditRepository.java`
- Test: `backend/src/test/java/com/sabyeguyz/schemacheck/IdCardSchemaHttpTest.java`

**Interfaces:**
- Produces: `CardReaderWorkstation` with getters/setters `getId, getName/setName, getBranch/setBranch, getTokenHash/setTokenHash, getPairingCodeHash/setPairingCodeHash, getPairingExpiresAt/setPairingExpiresAt, getStatus/setStatus, getLastSeenAt/setLastSeenAt, getCreatedAt, getCreatedBy/setCreatedBy`; repository methods `findByTokenHash(String)`, `findByPairingCodeHash(String)`, `findByBranchIdOrderByNameAsc(Long)`, `findAllByOrderByNameAsc()`; `IdCardReadAudit(Long workstationId, Long userId, Long branchId, String purpose, String outcome)` + `IdCardReadAuditRepository` (plain `JpaRepository<IdCardReadAudit, Long>`).

- [ ] **Step 1: Write the failing test**

```java
package com.sabyeguyz.schemacheck;

import static org.junit.jupiter.api.Assertions.*;

import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.idcard.CardReaderWorkstation;
import com.sabyeguyz.clinicv2.idcard.CardReaderWorkstationRepository;
import org.junit.jupiter.api.Test;

class IdCardSchemaHttpTest extends CoursePaymentHttpTestSupport {
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/backend":/w -w /w -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true maven:3.9-eclipse-temurin-17 mvn -q -Dtest=IdCardSchemaHttpTest test`
Expected: FAIL — compilation error (`CardReaderWorkstation` missing).

- [ ] **Step 3: Write the migration**

```sql
-- Counter workstations with a card reader, and the audit of reads (who, when, where, why; never the card's data).
CREATE TABLE card_reader_workstation (
    workstation_id      BIGSERIAL PRIMARY KEY,
    workstation_name    VARCHAR(100) NOT NULL,
    branch_id           BIGINT NOT NULL REFERENCES branch (branch_id),
    token_hash          CHAR(64),
    pairing_code_hash   CHAR(64),
    pairing_expires_at  TIMESTAMPTZ,
    workstation_status  VARCHAR(10) NOT NULL DEFAULT 'Active',
    last_seen_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT REFERENCES app_user (user_id),
    CONSTRAINT chk_card_reader_workstation_status CHECK (workstation_status IN ('Active', 'Revoked')),
    CONSTRAINT uq_card_reader_workstation_token UNIQUE (token_hash),
    CONSTRAINT uq_card_reader_workstation_pairing UNIQUE (pairing_code_hash)
);
CREATE INDEX idx_card_reader_workstation_branch ON card_reader_workstation (branch_id);

CREATE TABLE id_card_read_audit (
    audit_id        BIGSERIAL PRIMARY KEY,
    workstation_id  BIGINT NOT NULL REFERENCES card_reader_workstation (workstation_id),
    user_id         BIGINT NOT NULL REFERENCES app_user (user_id),
    branch_id       BIGINT NOT NULL REFERENCES branch (branch_id),
    purpose         VARCHAR(10) NOT NULL,
    outcome         VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_id_card_read_purpose CHECK (purpose IN ('REGISTER', 'LOOKUP')),
    CONSTRAINT chk_id_card_read_outcome CHECK (outcome IN
        ('SUCCESS', 'NO_CARD', 'NO_READER', 'ERROR', 'INVALID_CARD', 'TIMEOUT'))
);
CREATE INDEX idx_id_card_read_audit_workstation ON id_card_read_audit (workstation_id, created_at);
```

- [ ] **Step 4: Write the entities and repositories**

```java
package com.sabyeguyz.clinicv2.idcard;

import com.sabyeguyz.clinicv2.branch.Branch;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "card_reader_workstation")
public class CardReaderWorkstation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workstation_id")
    private Long id;

    @Column(name = "workstation_name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "pairing_code_hash", length = 64)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at")
    private Instant pairingExpiresAt;

    @Column(name = "workstation_status", nullable = false, length = 10)
    private String status = "Active";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getPairingCodeHash() { return pairingCodeHash; }
    public void setPairingCodeHash(String pairingCodeHash) { this.pairingCodeHash = pairingCodeHash; }
    public Instant getPairingExpiresAt() { return pairingExpiresAt; }
    public void setPairingExpiresAt(Instant pairingExpiresAt) { this.pairingExpiresAt = pairingExpiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardReaderWorkstationRepository extends JpaRepository<CardReaderWorkstation, Long> {
    Optional<CardReaderWorkstation> findByTokenHash(String tokenHash);

    Optional<CardReaderWorkstation> findByPairingCodeHash(String pairingCodeHash);

    List<CardReaderWorkstation> findByBranchIdOrderByNameAsc(Long branchId);

    List<CardReaderWorkstation> findAllByOrderByNameAsc();
}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import jakarta.persistence.*;
import java.time.Instant;

/** Who read a card, when, where and why. It never holds anything read from the card. */
@Entity
@Table(name = "id_card_read_audit")
public class IdCardReadAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "workstation_id", nullable = false)
    private Long workstationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 10)
    private String purpose;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdCardReadAudit() {}

    public IdCardReadAudit(Long workstationId, Long userId, Long branchId, String purpose, String outcome) {
        this.workstationId = workstationId;
        this.userId = userId;
        this.branchId = branchId;
        this.purpose = purpose;
        this.outcome = outcome;
    }

    public Long getId() { return id; }
    public Long getWorkstationId() { return workstationId; }
    public Long getUserId() { return userId; }
    public Long getBranchId() { return branchId; }
    public String getPurpose() { return purpose; }
    public String getOutcome() { return outcome; }
}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdCardReadAuditRepository extends JpaRepository<IdCardReadAudit, Long> {}
```

- [ ] **Step 5: Run to verify it passes**

Run: same command as Step 2. Expected: 2 tests PASS (application boots with `ddl-auto=validate`, so a mapping/column mismatch fails here).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V1023__id_card_reader.sql backend/src/main/java/com/sabyeguyz/clinicv2/idcard backend/src/test/java/com/sabyeguyz/schemacheck/IdCardSchemaHttpTest.java
git commit -m "feat(idcard): workstation and read-audit tables"
```

---

### Task 3: Workstation management and pairing

**Files:**
- Create: `.../idcard/TokenHasher.java`, `WorkstationService.java`, `WorkstationController.java`, `IdCardAgentController.java` (pair endpoint only for now)
- Modify: `backend/src/main/java/com/sabyeguyz/clinicv2/config/SecurityConfig.java` (permit `/api/id-card/agent/**`)
- Test: `backend/src/test/java/com/sabyeguyz/schemacheck/IdCardWorkstationHttpTest.java`

**Interfaces:**
- Consumes: Task 2 entity/repository.
- Produces:
  - `WorkstationService.Created(Long id, String name, String branchCode, String pairingCode, Instant pairingExpiresAt)`; `WorkstationService.Paired(String token, String workstationName, String branchCode)`
  - `Created create(String name, String branchCode)`, `Created newPairingCode(Long id)`, `void revoke(Long id)`, `Paired pair(String code)`, `CardReaderWorkstation authenticate(String bearerHeader)` (throws `ResponseStatusException` 401), `void touch(Long id)`, `boolean isOnline(CardReaderWorkstation w)`; constant `ONLINE_WINDOW = 40 s`
  - REST: `POST /api/id-card/workstations` `{name, branchCode}` (manager) → `Created`; `GET /api/id-card/workstations?branchCode=` (authenticated) → `[{id,name,branchCode,online,status}]`; `POST /api/id-card/workstations/{id}/pairing-code` (manager) → `Created`; `POST /api/id-card/workstations/{id}/revoke` (manager) → 204; `POST /api/id-card/agent/pair` `{code}` (no auth) → `Paired`

- [ ] **Step 1: Write the failing tests**

```java
package com.sabyeguyz.schemacheck;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IdCardWorkstationHttpTest extends CoursePaymentHttpTestSupport {
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/backend":/w -w /w -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true maven:3.9-eclipse-temurin-17 mvn -q -Dtest=IdCardWorkstationHttpTest test`
Expected: FAIL (404 for the new routes).

- [ ] **Step 3: Implement `TokenHasher`**

```java
package com.sabyeguyz.clinicv2.idcard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class TokenHasher {
    private TokenHasher() {}

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
```

- [ ] **Step 4: Implement `WorkstationService`**

```java
package com.sabyeguyz.clinicv2.idcard;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkstationService {
    public static final Duration ONLINE_WINDOW = Duration.ofSeconds(40);
    private static final Duration PAIRING_VALIDITY = Duration.ofMinutes(10);
    /** No 0/O/1/I so a code read aloud or typed from paper is not ambiguous. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public record Created(Long id, String name, String branchCode, String pairingCode, Instant pairingExpiresAt) {}

    public record Paired(String token, String workstationName, String branchCode) {}

    private final CardReaderWorkstationRepository workstations;
    private final BranchRepository branches;
    private final CurrentActor actor;
    private final SecureRandom random = new SecureRandom();

    public WorkstationService(CardReaderWorkstationRepository workstations, BranchRepository branches, CurrentActor actor) {
        this.workstations = workstations;
        this.branches = branches;
        this.actor = actor;
    }

    @Transactional
    public Created create(String name, String branchCode) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("ชื่อเครื่องอ่านบัตรต้องมี 1-100 ตัวอักษร");
        }
        Branch branch = branches.findByBranchCode(branchCode == null ? "" : branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode));
        CardReaderWorkstation workstation = new CardReaderWorkstation();
        workstation.setName(name.trim());
        workstation.setBranch(branch);
        workstation.setCreatedBy(actor.requireId());
        return withNewCode(workstations.save(workstation));
    }

    @Transactional
    public Created newPairingCode(Long id) {
        return withNewCode(require(id));
    }

    @Transactional
    public void revoke(Long id) {
        CardReaderWorkstation workstation = require(id);
        workstation.setStatus("Revoked");
        workstation.setTokenHash(null);
        workstation.setPairingCodeHash(null);
        workstation.setPairingExpiresAt(null);
    }

    @Transactional
    public Paired pair(String code) {
        CardReaderWorkstation workstation = workstations
                .findByPairingCodeHash(TokenHasher.sha256Hex(code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT)))
                .filter(w -> "Active".equals(w.getStatus()))
                .filter(w -> w.getPairingExpiresAt() != null && w.getPairingExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("รหัสจับคู่ไม่ถูกต้องหรือหมดอายุ"));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        workstation.setTokenHash(TokenHasher.sha256Hex(token));
        workstation.setPairingCodeHash(null);
        workstation.setPairingExpiresAt(null);
        return new Paired(token, workstation.getName(), workstation.getBranch().getBranchCode());
    }

    /** The workstation behind an {@code Authorization: Bearer <token>} header, or 401. */
    @Transactional(readOnly = true)
    public CardReaderWorkstation authenticate(String bearerHeader) {
        String token = bearerHeader != null && bearerHeader.startsWith("Bearer ") ? bearerHeader.substring(7).trim() : "";
        return workstations.findByTokenHash(TokenHasher.sha256Hex(token))
                .filter(w -> "Active".equals(w.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "โทเคนเครื่องอ่านบัตรไม่ถูกต้อง"));
    }

    @Transactional
    public void touch(Long id) {
        require(id).setLastSeenAt(Instant.now());
    }

    public boolean isOnline(CardReaderWorkstation workstation) {
        return workstation.getLastSeenAt() != null
                && workstation.getLastSeenAt().isAfter(Instant.now().minus(ONLINE_WINDOW));
    }

    public CardReaderWorkstation require(Long id) {
        return workstations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบเครื่องอ่านบัตร"));
    }

    private Created withNewCode(CardReaderWorkstation workstation) {
        String code;
        do {
            StringBuilder builder = new StringBuilder(8);
            for (int i = 0; i < 8; i++) builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = builder.toString();
        } while (workstations.findByPairingCodeHash(TokenHasher.sha256Hex(code)).isPresent());
        workstation.setPairingCodeHash(TokenHasher.sha256Hex(code));
        workstation.setPairingExpiresAt(Instant.now().plus(PAIRING_VALIDITY));
        return new Created(workstation.getId(), workstation.getName(), workstation.getBranch().getBranchCode(),
                code, workstation.getPairingExpiresAt());
    }
}
```

- [ ] **Step 5: Implement the controllers**

```java
package com.sabyeguyz.clinicv2.idcard;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.branch.Branch;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Manager: add, re-pair and revoke counter card readers. Everyone signed in: the readers of their own branch. */
@RestController
@RequestMapping("/api/id-card/workstations")
public class WorkstationController {
    public record CreateRequest(String name, String branchCode) {}

    public record WorkstationView(Long id, String name, String branchCode, boolean online, String status) {}

    private final WorkstationService service;
    private final CardReaderWorkstationRepository workstations;
    private final BranchAccessPolicy branchAccess;

    public WorkstationController(WorkstationService service, CardReaderWorkstationRepository workstations,
                                 BranchAccessPolicy branchAccess) {
        this.service = service;
        this.workstations = workstations;
        this.branchAccess = branchAccess;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public WorkstationService.Created create(@RequestBody CreateRequest request) {
        return service.create(request.name(), request.branchCode());
    }

    @PostMapping("/{id}/pairing-code")
    @PreAuthorize("hasRole('MANAGER')")
    public WorkstationService.Created newPairingCode(@PathVariable Long id) {
        return service.newPairingCode(id);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) {
        service.revoke(id);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<WorkstationView> list(@RequestParam(required = false) String branchCode) {
        Branch scope = branchAccess.scope(branchCode);
        boolean manager = branchAccess.isManager();
        List<CardReaderWorkstation> rows = scope == null
                ? workstations.findAllByOrderByNameAsc()
                : workstations.findByBranchIdOrderByNameAsc(scope.getId());
        return rows.stream()
                .filter(w -> manager || "Active".equals(w.getStatus()))
                .map(w -> new WorkstationView(w.getId(), w.getName(), w.getBranch().getBranchCode(),
                        service.isOnline(w), w.getStatus()))
                .toList();
    }
}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import org.springframework.web.bind.annotation.*;

/** The counter agent: pairs with a one-time code, then (Task 5) waits for read commands and returns results. */
@RestController
@RequestMapping("/api/id-card/agent")
public class IdCardAgentController {
    public record PairRequest(String code) {}

    private final WorkstationService workstations;

    public IdCardAgentController(WorkstationService workstations) {
        this.workstations = workstations;
    }

    @PostMapping("/pair")
    public WorkstationService.Paired pair(@RequestBody PairRequest request) {
        return workstations.pair(request.code());
    }
}
```

In `SecurityConfig.security(...)` add the agent path to the unauthenticated list:

```java
                        .requestMatchers(
                                "/api/auth/login",
                                "/actuator/health",
                                "/error",
                                "/api/id-card/agent/**")
                        .permitAll()
```

- [ ] **Step 6: Run to verify it passes**

Run: same command as Step 2. Expected: 6 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main backend/src/test/java/com/sabyeguyz/schemacheck/IdCardWorkstationHttpTest.java
git commit -m "feat(idcard): manager pairs counter card readers with a one-time code"
```

---

### Task 4: Read request store and rate limiter (plain Java)

**Files:**
- Create: `.../idcard/CardData.java`, `ReadRequestStore.java`, `ReadRateLimiter.java`
- Test: `backend/src/test/java/com/sabyeguyz/clinicv2/idcard/ReadRequestStoreTest.java`, `ReadRateLimiterTest.java`

**Interfaces:**
- Produces:
  - `record CardData(String nationalId, String titleTh, String firstNameTh, String middleNameTh, String lastNameTh, String fullNameEn, LocalDate birthDate, String gender, String address, LocalDate issueDate, LocalDate expiryDate, boolean lifetime)`
  - `ReadRequestStore.Purpose {REGISTER, LOOKUP}`; `ReadRequestStore.ReadRequest` with `id()`, `workstationId()`, `userId()`, `branchId()`, `purpose()`
  - `ReadRequestStore.Claim(Status status, ReadRequest request, Object payload, String outcome, String message)`; `Status {PENDING, DONE, FAILED}`
  - `ReadRequestStore(Clock, Duration ttl)`; no-arg constructor (UTC, 60 s)
  - `ReadRequest create(Long workstationId, Long userId, Long branchId, Purpose purpose)`
  - `Optional<ReadRequest> next(Long workstationId, Duration wait) throws InterruptedException`
  - `Optional<ReadRequest> find(String id)`
  - `void complete(String id, Long workstationId, Object payload)`; `void fail(String id, Long workstationId, String outcome, String message)` (both throw `NoSuchElementException` if the request does not belong to that workstation)
  - `Optional<Claim> claim(String id, Long userId)` — empty when unknown, expired-and-already-claimed or owned by another user; `DONE`/`FAILED` claims delete the request; a `PENDING` request past its expiry is deleted and returned as `FAILED`/`TIMEOUT`
  - `ReadRateLimiter(Clock, int maxPerMinute)`; `boolean tryAcquire(Long userId)`

- [ ] **Step 1: Write the failing tests**

```java
package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ReadRequestStoreTest {
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    final MutableClock clock = new MutableClock();
    final ReadRequestStore store = new ReadRequestStore(clock, Duration.ofSeconds(60));

    @Test
    void theAgentReceivesARequestOnItsOwnWorkstationOnly() throws Exception {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        assertTrue(store.next(8L, Duration.ZERO).isEmpty());
        assertEquals(request.id(), store.next(7L, Duration.ZERO).orElseThrow().id());
        assertTrue(store.next(7L, Duration.ZERO).isEmpty(), "a request is dispatched once");
    }

    @Test
    void theResultIsDeliveredOnceToTheUserWhoStartedTheRead() {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        assertEquals(ReadRequestStore.Status.PENDING, store.claim(request.id(), 1L).orElseThrow().status());

        store.complete(request.id(), 7L, "card");
        assertTrue(store.claim(request.id(), 2L).isEmpty(), "another user gets nothing");
        var claim = store.claim(request.id(), 1L).orElseThrow();
        assertEquals(ReadRequestStore.Status.DONE, claim.status());
        assertEquals("card", claim.payload());
        assertTrue(store.claim(request.id(), 1L).isEmpty(), "delivered once, then gone");
    }

    @Test
    void aFailureIsDeliveredOnceWithItsOutcome() {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.LOOKUP);
        store.fail(request.id(), 7L, "NO_CARD", "ไม่พบบัตร");
        var claim = store.claim(request.id(), 1L).orElseThrow();
        assertEquals(ReadRequestStore.Status.FAILED, claim.status());
        assertEquals("NO_CARD", claim.outcome());
        assertTrue(store.claim(request.id(), 1L).isEmpty());
    }

    @Test
    void aRequestThatNobodyAnsweredTimesOut() {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        clock.advance(Duration.ofSeconds(61));
        var claim = store.claim(request.id(), 1L).orElseThrow();
        assertEquals(ReadRequestStore.Status.FAILED, claim.status());
        assertEquals("TIMEOUT", claim.outcome());
        assertTrue(store.claim(request.id(), 1L).isEmpty());
    }

    @Test
    void anExpiredRequestIsNotDispatchedToTheAgent() throws Exception {
        store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        clock.advance(Duration.ofSeconds(61));
        assertTrue(store.next(7L, Duration.ZERO).isEmpty());
    }

    @Test
    void aResultFromAnotherWorkstationIsRejected() {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        assertThrows(NoSuchElementException.class, () -> store.complete(request.id(), 8L, "card"));
        assertThrows(NoSuchElementException.class, () -> store.fail(request.id(), 8L, "ERROR", "x"));
    }
}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReadRateLimiterTest {
    @Test
    void allowsTheLimitPerMinutePerUserThenRecovers() {
        var clock = new ReadRequestStoreTest.MutableClock();
        var limiter = new ReadRateLimiter(clock, 3);
        assertTrue(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(1L));
        assertFalse(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(2L), "another user has their own allowance");
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.tryAcquire(1L));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/backend":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q -Dtest='ReadRequestStoreTest,ReadRateLimiterTest' test`
Expected: FAIL (classes missing).

- [ ] **Step 3: Implement**

```java
package com.sabyeguyz.clinicv2.idcard;

import java.time.LocalDate;

/** What the agent read from the chip (no photo). Dates are Gregorian; a lifetime card has no expiry date. */
public record CardData(
        String nationalId,
        String titleTh,
        String firstNameTh,
        String middleNameTh,
        String lastNameTh,
        String fullNameEn,
        LocalDate birthDate,
        String gender,
        String address,
        LocalDate issueDate,
        LocalDate expiryDate,
        boolean lifetime) {}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Card reads in flight. Everything is in memory: a result is kept for the time to live, handed to the user who started the
 * read exactly once, and then dropped. Nothing here is ever written to a log or the database.
 */
@Component
public class ReadRequestStore {
    public enum Purpose { REGISTER, LOOKUP }

    public enum Status { PENDING, DONE, FAILED }

    public record ReadRequest(String id, Long workstationId, Long userId, Long branchId, Purpose purpose, Instant expiresAt) {}

    public record Claim(Status status, ReadRequest request, Object payload, String outcome, String message) {}

    private static final class Entry {
        final ReadRequest request;
        volatile Status status = Status.PENDING;
        volatile Object payload;
        volatile String outcome;
        volatile String message;

        Entry(ReadRequest request) {
            this.request = request;
        }
    }

    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Long, LinkedBlockingQueue<ReadRequest>> queues = new ConcurrentHashMap<>();

    @Autowired
    public ReadRequestStore() {
        this(Clock.systemUTC(), Duration.ofSeconds(60));
    }

    public ReadRequestStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public ReadRequest create(Long workstationId, Long userId, Long branchId, Purpose purpose) {
        purgeExpired();
        ReadRequest request = new ReadRequest(UUID.randomUUID().toString(), workstationId, userId, branchId, purpose,
                clock.instant().plus(ttl));
        entries.put(request.id(), new Entry(request));
        queues.computeIfAbsent(workstationId, key -> new LinkedBlockingQueue<>()).add(request);
        return request;
    }

    /** Waits up to {@code wait} for a request for this workstation; an expired request is skipped. */
    public Optional<ReadRequest> next(Long workstationId, Duration wait) throws InterruptedException {
        LinkedBlockingQueue<ReadRequest> queue = queues.computeIfAbsent(workstationId, key -> new LinkedBlockingQueue<>());
        Instant giveUp = clock.instant().plus(wait);
        Duration left = wait;
        while (true) {
            ReadRequest request = left.isZero() || left.isNegative() ? queue.poll() : queue.poll(left.toMillis(), TimeUnit.MILLISECONDS);
            if (request == null) return Optional.empty();
            if (request.expiresAt().isAfter(clock.instant()) && entries.containsKey(request.id())) return Optional.of(request);
            left = Duration.between(clock.instant(), giveUp);
        }
    }

    public Optional<ReadRequest> find(String id) {
        return Optional.ofNullable(entries.get(id)).map(entry -> entry.request);
    }

    public void complete(String id, Long workstationId, Object payload) {
        Entry entry = owned(id, workstationId);
        entry.payload = payload;
        entry.status = Status.DONE;
    }

    public void fail(String id, Long workstationId, String outcome, String message) {
        Entry entry = owned(id, workstationId);
        entry.outcome = outcome;
        entry.message = message;
        entry.status = Status.FAILED;
    }

    /** The state of a read for the user who started it; a finished state is delivered once and removed. */
    public Optional<Claim> claim(String id, Long userId) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.request.userId().equals(userId)) return Optional.empty();
        if (entry.status == Status.PENDING) {
            if (entry.request.expiresAt().isAfter(clock.instant())) {
                return Optional.of(new Claim(Status.PENDING, entry.request, null, null, null));
            }
            entries.remove(id);
            return Optional.of(new Claim(Status.FAILED, entry.request, null, "TIMEOUT", "หมดเวลา ไม่ได้รับข้อมูลจากเครื่องอ่านบัตร"));
        }
        entries.remove(id);
        return Optional.of(new Claim(entry.status, entry.request, entry.payload, entry.outcome, entry.message));
    }

    private Entry owned(String id, Long workstationId) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.request.workstationId().equals(workstationId)) {
            throw new NoSuchElementException("read request not found");
        }
        return entry;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> entry.status != Status.PENDING
                ? entry.request.expiresAt().plus(ttl).isBefore(now)
                : entry.request.expiresAt().plus(ttl).isBefore(now));
    }
}
```

```java
package com.sabyeguyz.clinicv2.idcard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A user may start only a few card reads per minute. */
@Component
public class ReadRateLimiter {
    private final Clock clock;
    private final int maxPerMinute;
    private final Map<Long, ArrayDeque<Instant>> starts = new ConcurrentHashMap<>();

    @Autowired
    public ReadRateLimiter() {
        this(Clock.systemUTC(), 20);
    }

    public ReadRateLimiter(Clock clock, int maxPerMinute) {
        this.clock = clock;
        this.maxPerMinute = maxPerMinute;
    }

    public boolean tryAcquire(Long userId) {
        ArrayDeque<Instant> window = starts.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            Instant now = clock.instant();
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) window.pollFirst();
            if (window.size() >= maxPerMinute) return false;
            window.addLast(now);
            return true;
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: same command as Step 2. Expected: 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/sabyeguyz/clinicv2/idcard backend/src/test/java/com/sabyeguyz/clinicv2/idcard
git commit -m "feat(idcard): in-memory card reads, delivered once, with a per-user rate limit"
```

---

### Task 5: Read orchestration, agent endpoints, staff endpoints, audit

**Files:**
- Create: `.../idcard/IdCardService.java`, `IdCardReadController.java`
- Modify: `.../idcard/IdCardAgentController.java` (add `next`, `result`)
- Test: `backend/src/test/java/com/sabyeguyz/schemacheck/IdCardReadHttpTest.java`

**Interfaces:**
- Consumes: Tasks 1–4 (`ThaiNationalId`, `WorkstationService`, `ReadRequestStore`, `ReadRateLimiter`, `CardData`, audit repo, `PatientRepository.findByNationalId`, `BranchAccessPolicy.scope(null)`, `CurrentActor.requireId()`).
- Produces (REST):
  - `POST /api/id-card/reads` `{workstationId, purpose}` → 202 `{id, expiresAt}`; 404 unknown/revoked workstation; 403 other branch; 409 workstation offline; 429 too many
  - `GET /api/id-card/reads/{id}` → `{status: PENDING|DONE|FAILED, outcome?, message?, card?, lookup?}`; 404 once delivered/unknown/another user's
  - `GET /api/id-card/agent/next?waitSeconds=` (Bearer token) → 200 `{requestId, purpose}` or 204
  - `POST /api/id-card/agent/result` (Bearer token) `{requestId, status: OK|NO_CARD|NO_READER|ERROR, message?, card?}` → 204
- `lookup` = `{exists: boolean, patient: {id, hn, fullName} | null}`; a LOOKUP never returns `card`.

- [ ] **Step 1: Write the failing tests**

```java
package com.sabyeguyz.schemacheck;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdCardReadHttpTest extends CoursePaymentHttpTestSupport {
    static final String VALID_ID = "1101700005119";
    String agentToken;
    long workstationId;

    @BeforeEach
    void pairAWorkstation() {
        db.execute("TRUNCATE TABLE id_card_read_audit, card_reader_workstation RESTART IDENTITY CASCADE");
        var created = reply("POST", "/api/id-card/workstations", Map.of("name", "เคาน์เตอร์ 1", "branchCode", "01")).body();
        workstationId = created.path("id").asLong();
        agentToken = replyAs(null, "POST", "/api/id-card/agent/pair", Map.of("code", created.path("pairingCode").asText()))
                .body().path("token").asText();
        // the agent has polled once, so the workstation is online
        assertEquals(204, replyAs(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
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

    String start(String token, String purpose) {
        HttpResult started = replyAs(token, "POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", purpose));
        assertEquals(202, started.status(), started.body().toString());
        return started.body().path("id").asText();
    }

    String agentTakes() {
        HttpResult next = replyAs(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=2", null);
        assertEquals(200, next.status(), next.body().toString());
        return next.body().path("requestId").asText();
    }

    void agentAnswers(String requestId, String status, Map<String, Object> card) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("status", status);
        body.put("card", card);
        assertEquals(204, replyAs(agentToken, "POST", "/api/id-card/agent/result", body).status());
    }

    @Test
    void registerReadDeliversTheCardOnceToTheUserWhoStartedIt() {
        String id = start(token, "REGISTER");
        assertEquals("REGISTER", replyAs(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=2", null).body().path("purpose").asText());
        assertEquals("PENDING", reply("GET", "/api/id-card/reads/" + id, null).body().path("status").asText());

        agentAnswers(id, "OK", card(VALID_ID));
        HttpResult done = reply("GET", "/api/id-card/reads/" + id, null);
        assertEquals("DONE", done.body().path("status").asText());
        assertEquals(VALID_ID, done.body().path("card").path("nationalId").asText());
        assertEquals("1997-01-31", done.body().path("card").path("birthDate").asText());

        assertEquals(404, reply("GET", "/api/id-card/reads/" + id, null).status(), "delivered once");
        assertEquals("SUCCESS", db.queryForObject("select outcome from id_card_read_audit", String.class));
    }

    @Test
    void lookupFindsAnExistingPatientAndNeverReturnsTheCard() {
        db.update("update patient set national_id=? where patient_id=?", VALID_ID, patientId);
        String id = start(token, "LOOKUP");
        agentTakes();
        agentAnswers(id, "OK", card(VALID_ID));

        HttpResult done = reply("GET", "/api/id-card/reads/" + id, null);
        assertEquals("DONE", done.body().path("status").asText());
        assertTrue(done.body().path("lookup").path("exists").asBoolean());
        assertNotNull(done.body().path("lookup").path("patient").path("hn").asText(null));
        String text = done.body().toString();
        assertFalse(done.body().has("card"));
        assertFalse(text.contains("1997-01-31") || text.contains(VALID_ID) || text.contains("ลาดพร้าว"), text);
    }

    @Test
    void lookupOfAnUnknownNumberSaysSo() {
        String id = start(token, "LOOKUP");
        agentTakes();
        agentAnswers(id, "OK", card(VALID_ID));
        HttpResult done = reply("GET", "/api/id-card/reads/" + id, null);
        assertFalse(done.body().path("lookup").path("exists").asBoolean());
        assertTrue(done.body().path("lookup").path("patient").isNull());
    }

    @Test
    void aNumberWithAWrongCheckDigitIsRefused() {
        String id = start(token, "REGISTER");
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
            String id = start(token, "REGISTER");
            agentTakes();
            agentAnswers(id, outcome, null);
            HttpResult failed = reply("GET", "/api/id-card/reads/" + id, null);
            assertEquals("FAILED", failed.body().path("status").asText());
            assertEquals(outcome, failed.body().path("outcome").asText());
        }
        assertEquals(3L, db.queryForObject("select count(*) from id_card_read_audit", Long.class));
    }

    @Test
    void aWorkstationThatIsNotPollingIsOffline() {
        db.update("update card_reader_workstation set last_seen_at = now() - interval '5 minutes'");
        HttpResult started = reply("POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "REGISTER"));
        assertEquals(409, started.status());
    }

    @Test
    void anotherUsersReadIsInvisible() {
        String id = start(token, "REGISTER");
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
        assertEquals(401, replyAs(agentToken, "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
        assertEquals(401, replyAs("not-a-token", "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
        assertEquals(401, replyAs(null, "GET", "/api/id-card/agent/next?waitSeconds=0", null).status());
    }

    @Test
    void tooManyStartsInAMinuteAreRefused() {
        int accepted = 0;
        int refused = 0;
        for (int i = 0; i < 22; i++) {
            int status = reply("POST", "/api/id-card/reads", Map.of("workstationId", workstationId, "purpose", "LOOKUP")).status();
            if (status == 202) accepted++;
            if (status == 429) refused++;
        }
        assertEquals(20, accepted);
        assertEquals(2, refused);
    }

    @Test
    void auditRecordsTheReadWithoutAnyCardData() {
        String id = start(token, "REGISTER");
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/backend":/w -w /w -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true maven:3.9-eclipse-temurin-17 mvn -q -Dtest=IdCardReadHttpTest test`
Expected: FAIL (404/405 for the new routes; `@BeforeEach` fails at `agent/next`).

- [ ] **Step 3: Implement `IdCardService`**

```java
package com.sabyeguyz.clinicv2.idcard;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdCardService {
    static final Duration AGENT_MAX_WAIT = Duration.ofSeconds(25);

    public record Started(String id, Instant expiresAt) {}

    public record AgentCommand(String requestId, String purpose) {}

    public record AgentResult(String requestId, String status, String message, CardData card) {}

    public record PatientBrief(Long id, String hn, String fullName) {}

    public record LookupResult(boolean exists, PatientBrief patient) {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record ReadView(String status, String outcome, String message, CardData card, LookupResult lookup) {}

    private final ReadRequestStore store;
    private final ReadRateLimiter limiter;
    private final WorkstationService workstations;
    private final PatientRepository patients;
    private final IdCardReadAuditRepository audits;
    private final CurrentActor actor;
    private final BranchAccessPolicy branchAccess;

    public IdCardService(ReadRequestStore store, ReadRateLimiter limiter, WorkstationService workstations,
                         PatientRepository patients, IdCardReadAuditRepository audits, CurrentActor actor,
                         BranchAccessPolicy branchAccess) {
        this.store = store;
        this.limiter = limiter;
        this.workstations = workstations;
        this.patients = patients;
        this.audits = audits;
        this.actor = actor;
        this.branchAccess = branchAccess;
    }

    @Transactional
    public Started start(Long workstationId, String purposeText) {
        Long userId = actor.requireId();
        ReadRequestStore.Purpose purpose = parsePurpose(purposeText);
        CardReaderWorkstation workstation = workstations.require(workstationId);
        if (!"Active".equals(workstation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบเครื่องอ่านบัตร");
        }
        Branch scope = branchAccess.scope(null);
        if (scope != null && !scope.getId().equals(workstation.getBranch().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "เครื่องอ่านบัตรนี้อยู่คนละสาขากับผู้ใช้");
        }
        if (!limiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "สั่งอ่านบัตรบ่อยเกินไป รอสักครู่");
        }
        if (!workstations.isOnline(workstation)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "โปรแกรมตัวกลางของเครื่องนี้ออฟไลน์");
        }
        ReadRequestStore.ReadRequest request = store.create(workstationId, userId, workstation.getBranch().getId(), purpose);
        return new Started(request.id(), request.expiresAt());
    }

    /** Called by the agent: holds the request for up to {@code waitSeconds} until a command arrives. */
    public Optional<AgentCommand> next(CardReaderWorkstation workstation, int waitSeconds) {
        workstations.touch(workstation.getId());
        Duration wait = Duration.ofSeconds(Math.max(0, Math.min(waitSeconds, AGENT_MAX_WAIT.toSeconds())));
        try {
            return store.next(workstation.getId(), wait)
                    .map(request -> new AgentCommand(request.id(), request.purpose().name()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Transactional
    public void agentResult(CardReaderWorkstation workstation, AgentResult result) {
        ReadRequestStore.ReadRequest request = store.find(result.requestId())
                .filter(found -> found.workstationId().equals(workstation.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบคำขออ่านบัตร"));
        String status = result.status() == null ? "" : result.status();
        switch (status) {
            case "OK" -> finishCard(workstation, request, result.card());
            case "NO_CARD" -> failAndAudit(workstation, request, "NO_CARD", "ไม่พบบัตร ใส่บัตรแล้วกดอ่านใหม่");
            case "NO_READER" -> failAndAudit(workstation, request, "NO_READER", "ไม่พบเครื่องอ่านบัตร ตรวจสายและเสียบเครื่องอ่านใหม่");
            default -> failAndAudit(workstation, request, "ERROR",
                    result.message() == null || result.message().isBlank() ? "อ่านบัตรไม่สำเร็จ ลองใหม่อีกครั้ง" : result.message());
        }
    }

    @Transactional
    public ReadView poll(String id) {
        ReadRequestStore.Claim claim = store.claim(id, actor.requireId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบคำขออ่านบัตร"));
        return switch (claim.status()) {
            case PENDING -> new ReadView("PENDING", null, null, null, null);
            case FAILED -> {
                if ("TIMEOUT".equals(claim.outcome())) audit(claim.request(), "TIMEOUT");
                yield new ReadView("FAILED", claim.outcome(), claim.message(), null, null);
            }
            case DONE -> claim.payload() instanceof LookupResult lookup
                    ? new ReadView("DONE", null, null, null, lookup)
                    : new ReadView("DONE", null, null, (CardData) claim.payload(), null);
        };
    }

    private void finishCard(CardReaderWorkstation workstation, ReadRequestStore.ReadRequest request, CardData card) {
        if (card == null || !ThaiNationalId.isValid(card.nationalId())) {
            failAndAudit(workstation, request, "INVALID_CARD", "เลขบัตรไม่ผ่านการตรวจหลักตรวจสอบ อ่านบัตรใหม่หรือกรอกเอง");
            return;
        }
        if (request.purpose() == ReadRequestStore.Purpose.LOOKUP) {
            // the card's personal data stays here: the browser only learns whether the patient exists
            LookupResult lookup = patients.findByNationalId(card.nationalId())
                    .map(patient -> new LookupResult(true, new PatientBrief(patient.getId(), patient.getPatientHn(), fullName(patient))))
                    .orElseGet(() -> new LookupResult(false, null));
            store.complete(request.id(), workstation.getId(), lookup);
        } else {
            store.complete(request.id(), workstation.getId(), card);
        }
        audit(request, "SUCCESS");
    }

    private void failAndAudit(CardReaderWorkstation workstation, ReadRequestStore.ReadRequest request, String outcome, String message) {
        store.fail(request.id(), workstation.getId(), outcome, message);
        audit(request, outcome);
    }

    private void audit(ReadRequestStore.ReadRequest request, String outcome) {
        audits.save(new IdCardReadAudit(request.workstationId(), request.userId(), request.branchId(), request.purpose().name(), outcome));
    }

    private static ReadRequestStore.Purpose parsePurpose(String value) {
        try {
            return ReadRequestStore.Purpose.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("วัตถุประสงค์ต้องเป็น REGISTER หรือ LOOKUP");
        }
    }

    private static String fullName(Patient patient) {
        return (patient.getPatientFname() + " " + patient.getPatientLname()).trim();
    }
}
```

- [ ] **Step 4: Add the controllers**

```java
package com.sabyeguyz.clinicv2.idcard;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Staff: start a card read at a counter workstation and poll for its one-time result. */
@RestController
@RequestMapping("/api/id-card/reads")
public class IdCardReadController {
    public record StartRequest(Long workstationId, String purpose) {}

    private final IdCardService service;

    public IdCardReadController(IdCardService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IdCardService.Started start(@RequestBody StartRequest request) {
        return service.start(request.workstationId(), request.purpose());
    }

    @GetMapping("/{id}")
    public IdCardService.ReadView poll(@PathVariable String id) {
        return service.poll(id);
    }
}
```

Replace `IdCardAgentController` with the full version:

```java
package com.sabyeguyz.clinicv2.idcard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The counter agent: pairs with a one-time code, waits for read commands (long-poll) and returns each result. */
@RestController
@RequestMapping("/api/id-card/agent")
public class IdCardAgentController {
    public record PairRequest(String code) {}

    private final WorkstationService workstations;
    private final IdCardService service;

    public IdCardAgentController(WorkstationService workstations, IdCardService service) {
        this.workstations = workstations;
        this.service = service;
    }

    @PostMapping("/pair")
    public WorkstationService.Paired pair(@RequestBody PairRequest request) {
        return workstations.pair(request.code());
    }

    @GetMapping("/next")
    public ResponseEntity<IdCardService.AgentCommand> next(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "25") int waitSeconds) {
        CardReaderWorkstation workstation = workstations.authenticate(authorization);
        return service.next(workstation, waitSeconds)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void result(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestBody IdCardService.AgentResult result) {
        service.agentResult(workstations.authenticate(authorization), result);
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: same command as Step 2, then the whole idcard group: `... mvn -q -Dtest='IdCard*HttpTest,ReadRequestStoreTest,ReadRateLimiterTest,ThaiNationalIdTest' test`
Expected: all PASS. If `lookupFindsAnExistingPatient...` fails because `Patient` has no `getPatientFname()/getPatientLname()/getPatientHn()`, use the getters that `PatientController.fullName(...)` uses (see `PatientController.java`), keeping the output `"<first> <last>"`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main backend/src/test/java/com/sabyeguyz/schemacheck/IdCardReadHttpTest.java
git commit -m "feat(idcard): start a card read, hand it to the agent, deliver the result once"
```

---

## Part B — Agent (`id-card-agent/`, same branch as Part A)

### Task 6: Project skeleton and the chip-data parser

**Files:**
- Create: `id-card-agent/pom.xml`, `id-card-agent/src/main/java/com/sabyeguyz/idcard/agent/CardData.java`, `.../ThaiIdCardParser.java`
- Test: `id-card-agent/src/test/java/com/sabyeguyz/idcard/agent/ThaiIdCardParserTest.java`

**Interfaces:**
- Produces:
  - `record CardData(...)` — identical fields and names to the backend `CardData` (Task 4)
  - `ThaiIdCardParser.Field {CID, NAME_TH, NAME_EN, BIRTH, GENDER, ISSUE, EXPIRY, ADDRESS}`
  - `static CardData ThaiIdCardParser.parse(Map<Field, byte[]> raw)` — throws `IllegalArgumentException` when the number is not 13 digits
  - `static LocalDate ThaiIdCardParser.buddhistDate(String yyyymmdd)` — `null` for blank or `99999999`

- [ ] **Step 1: Create the Maven project**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.sabyeguyz</groupId>
    <artifactId>id-card-agent</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jackson.version>2.17.2</jackson.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.sabyeguyz.idcard.agent.AgentMain</mainClass>
                                </transformer>
                            </transformers>
                            <finalName>id-card-agent</finalName>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write the failing test**

```java
package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThaiIdCardParserTest {
    static final Charset TIS620 = Charset.forName("TIS-620");

    static byte[] tis(String text) {
        return text.getBytes(TIS620);
    }

    static Map<ThaiIdCardParser.Field, byte[]> sample() {
        Map<ThaiIdCardParser.Field, byte[]> raw = new EnumMap<>(ThaiIdCardParser.Field.class);
        raw.put(ThaiIdCardParser.Field.CID, tis("1101700005119"));
        raw.put(ThaiIdCardParser.Field.NAME_TH, tis("นาย#สมชาย##ใจดี   "));
        raw.put(ThaiIdCardParser.Field.NAME_EN, tis("Mr.#Somchai##Jaidee     "));
        raw.put(ThaiIdCardParser.Field.BIRTH, tis("25400131"));
        raw.put(ThaiIdCardParser.Field.GENDER, tis("1"));
        raw.put(ThaiIdCardParser.Field.ISSUE, tis("25630202"));
        raw.put(ThaiIdCardParser.Field.EXPIRY, tis("99999999"));
        raw.put(ThaiIdCardParser.Field.ADDRESS, tis("88/8#หมู่ที่ 3##ซอยลาดพร้าว 3#ถนนลาดพร้าว#ตำบลจรเข้บัว#อำเภอลาดพร้าว#จังหวัดกรุงเทพมหานคร  "));
        return raw;
    }

    @Test
    void parsesAThaiCardIntoGregorianDatesAndSeparateNames() {
        CardData card = ThaiIdCardParser.parse(sample());
        assertEquals("1101700005119", card.nationalId());
        assertEquals("นาย", card.titleTh());
        assertEquals("สมชาย", card.firstNameTh());
        assertNull(card.middleNameTh());
        assertEquals("ใจดี", card.lastNameTh());
        assertEquals("Somchai Jaidee", card.fullNameEn());
        assertEquals(LocalDate.of(1997, 1, 31), card.birthDate());
        assertEquals("M", card.gender());
        assertEquals(LocalDate.of(2020, 2, 2), card.issueDate());
        assertNull(card.expiryDate());
        assertTrue(card.lifetime());
        assertEquals("88/8 หมู่ที่ 3 ซอยลาดพร้าว 3 ถนนลาดพร้าว ตำบลจรเข้บัว อำเภอลาดพร้าว จังหวัดกรุงเทพมหานคร", card.address());
    }

    @Test
    void keepsAMiddleNameAndAFemaleGenderAndAnExpiryDate() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.NAME_TH, tis("นางสาว#สมหญิง#กลาง#ใจดี"));
        raw.put(ThaiIdCardParser.Field.GENDER, tis("2"));
        raw.put(ThaiIdCardParser.Field.EXPIRY, tis("25681231"));
        CardData card = ThaiIdCardParser.parse(raw);
        assertEquals("กลาง", card.middleNameTh());
        assertEquals("F", card.gender());
        assertFalse(card.lifetime());
        assertEquals(LocalDate.of(2025, 12, 31), card.expiryDate());
    }

    @Test
    void trailingPaddingBytesAreIgnored() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.CID, new byte[] {'1','1','0','1','7','0','0','0','0','5','1','1','9', 0, 0});
        assertEquals("1101700005119", ThaiIdCardParser.parse(raw).nationalId());
    }

    @Test
    void anAddressLongerThanTheDatabaseColumnIsCut() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.ADDRESS, tis("ก".repeat(400)));
        assertEquals(255, ThaiIdCardParser.parse(raw).address().length());
    }

    @Test
    void aNumberThatIsNot13DigitsIsRejected() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.CID, tis("12345"));
        assertThrows(IllegalArgumentException.class, () -> ThaiIdCardParser.parse(raw));
    }

    @Test
    void buddhistDatesConvertAndLifetimeIsNull() {
        assertEquals(LocalDate.of(1997, 1, 31), ThaiIdCardParser.buddhistDate("25400131"));
        assertNull(ThaiIdCardParser.buddhistDate("99999999"));
        assertNull(ThaiIdCardParser.buddhistDate("   "));
        assertNull(ThaiIdCardParser.buddhistDate(null));
    }

    @Test
    void aMissingOptionalFieldBecomesNull() {
        var raw = sample();
        raw.remove(ThaiIdCardParser.Field.NAME_EN);
        raw.remove(ThaiIdCardParser.Field.ADDRESS);
        CardData card = ThaiIdCardParser.parse(raw);
        assertNull(card.fullNameEn());
        assertNull(card.address());
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `docker run --rm -v "$PWD/id-card-agent":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q test`
Expected: FAIL (classes missing).

- [ ] **Step 4: Implement**

```java
package com.sabyeguyz.idcard.agent;

import java.time.LocalDate;

/** What is read from the chip (no photo). Same shape the backend accepts. */
public record CardData(
        String nationalId,
        String titleTh,
        String firstNameTh,
        String middleNameTh,
        String lastNameTh,
        String fullNameEn,
        LocalDate birthDate,
        String gender,
        String address,
        LocalDate issueDate,
        LocalDate expiryDate,
        boolean lifetime) {}
```

```java
package com.sabyeguyz.idcard.agent;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns the raw bytes of each chip field (TIS-620 text, '#'-separated parts, Buddhist-era dates) into a CardData. */
final class ThaiIdCardParser {
    enum Field { CID, NAME_TH, NAME_EN, BIRTH, GENDER, ISSUE, EXPIRY, ADDRESS }

    private static final Charset TIS620 = Charset.forName("TIS-620");
    private static final int ADDRESS_MAX = 255;

    private ThaiIdCardParser() {}

    static CardData parse(Map<Field, byte[]> raw) {
        String nationalId = text(raw.get(Field.CID));
        if (nationalId == null || !nationalId.matches("[0-9]{13}")) {
            throw new IllegalArgumentException("เลขบัตรอ่านได้ไม่ครบ 13 หลัก");
        }
        String[] th = parts(text(raw.get(Field.NAME_TH)), 4);
        String[] en = parts(text(raw.get(Field.NAME_EN)), 4);
        String expiryText = text(raw.get(Field.EXPIRY));
        boolean lifetime = "99999999".equals(expiryText);
        return new CardData(
                nationalId,
                blankToNull(th[0]), blankToNull(th[1]), blankToNull(th[2]), blankToNull(th[3]),
                joinNonBlank(" ", en[1], en[2], en[3]),
                buddhistDate(text(raw.get(Field.BIRTH))),
                gender(text(raw.get(Field.GENDER))),
                address(text(raw.get(Field.ADDRESS))),
                buddhistDate(text(raw.get(Field.ISSUE))),
                lifetime ? null : buddhistDate(expiryText),
                lifetime);
    }

    static LocalDate buddhistDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank() || "99999999".equals(yyyymmdd.trim())) return null;
        String value = yyyymmdd.trim();
        if (!value.matches("[0-9]{8}")) return null;
        try {
            return LocalDate.of(Integer.parseInt(value.substring(0, 4)) - 543,
                    Integer.parseInt(value.substring(4, 6)), Integer.parseInt(value.substring(6, 8)));
        } catch (java.time.DateTimeException invalid) {
            return null;
        }
    }

    private static String text(byte[] bytes) {
        if (bytes == null) return null;
        String value = new String(bytes, TIS620).replace('\u0000', ' ').strip();
        return value.isEmpty() ? null : value;
    }

    private static String[] parts(String value, int count) {
        String[] out = new String[count];
        java.util.Arrays.fill(out, "");
        if (value == null) return out;
        String[] split = value.split("#", -1);
        for (int i = 0; i < count && i < split.length; i++) out[i] = split[i].strip();
        return out;
    }

    private static String gender(String value) {
        if ("1".equals(value)) return "M";
        if ("2".equals(value)) return "F";
        return null;
    }

    private static String address(String value) {
        if (value == null) return null;
        List<String> pieces = new ArrayList<>();
        for (String piece : value.split("#", -1)) if (!piece.isBlank()) pieces.add(piece.strip());
        String joined = String.join(" ", pieces);
        if (joined.isEmpty()) return null;
        return joined.length() > ADDRESS_MAX ? joined.substring(0, ADDRESS_MAX) : joined;
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> pieces = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) pieces.add(value);
        return pieces.isEmpty() ? null : String.join(separator, pieces);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: same command as Step 3. Expected: 7 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add id-card-agent
git commit -m "feat(agent): parse the fields read from a Thai ID card chip"
```

---

### Task 7: APDU command sequence (card reader logic without hardware)

**Files:**
- Create: `.../agent/ApduChannel.java`, `.../agent/ThaiIdCardReader.java`
- Test: `id-card-agent/src/test/java/com/sabyeguyz/idcard/agent/ThaiIdCardReaderTest.java`

**Interfaces:**
- Consumes: Task 6 `ThaiIdCardParser`, `CardData`.
- Produces:
  - `interface ApduChannel { Response transmit(byte[] command) throws IOException; record Response(byte[] data, int sw) {} }`
  - `ThaiIdCardReader.read(ApduChannel): CardData` — selects the card applet, reads each field, handles a `61 xx` "more data" answer with GET RESPONSE; throws `IOException` on any other status word
  - `static byte[] ThaiIdCardReader.hex(String)`

The command bytes below follow what community Thai-ID-reader code uses; **they must be confirmed against a real card (Task 18)** before the feature is called done.

- [ ] **Step 1: Write the failing test**

```java
package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThaiIdCardReaderTest {
    static final Charset TIS620 = Charset.forName("TIS-620");

    /** A card that answers each command from a table; commands it has not been told about answer 6A 82. */
    static final class FakeCard implements ApduChannel {
        final Map<String, byte[]> answers = new HashMap<>();
        final Map<String, byte[]> pendingResponse = new HashMap<>();
        final List<String> log = new ArrayList<>();
        boolean useGetResponse = false;

        @Override
        public Response transmit(byte[] command) {
            String key = java.util.HexFormat.of().formatHex(command).toUpperCase();
            log.add(key);
            if (key.startsWith("00C00000")) { // GET RESPONSE
                byte[] data = pendingResponse.remove("last");
                return new Response(data, 0x9000);
            }
            byte[] data = answers.get(key);
            if (data == null) return new Response(new byte[0], 0x6A82);
            if (key.startsWith("00A40400")) return new Response(new byte[0], 0x9000);
            if (useGetResponse) {
                pendingResponse.put("last", data);
                return new Response(new byte[0], 0x6100 | data.length);
            }
            return new Response(data, 0x9000);
        }
    }

    static FakeCard thaiCard() {
        FakeCard card = new FakeCard();
        card.answers.put("00A4040008A000000054480001", new byte[0]);
        card.answers.put("80B00004020 00D".replace(" ", ""), "1101700005119".getBytes(TIS620));
        card.answers.put("80B00011020064", "นาย#สมชาย##ใจดี".getBytes(TIS620));
        card.answers.put("80B00075020064", "Mr.#Somchai##Jaidee".getBytes(TIS620));
        card.answers.put("80B000D9020008", "25400131".getBytes(TIS620));
        card.answers.put("80B000E1020001", "1".getBytes(TIS620));
        card.answers.put("80B00167020008", "25630202".getBytes(TIS620));
        card.answers.put("80B0016F020008", "99999999".getBytes(TIS620));
        card.answers.put("80B01579020064", "88/8#หมู่ที่ 3#ตำบลจรเข้บัว".getBytes(TIS620));
        return card;
    }

    @Test
    void readsEveryFieldAfterSelectingTheCardApplet() throws IOException {
        FakeCard card = thaiCard();
        CardData data = new ThaiIdCardReader().read(card);
        assertEquals("1101700005119", data.nationalId());
        assertEquals("สมชาย", data.firstNameTh());
        assertEquals("Somchai Jaidee", data.fullNameEn());
        assertTrue(data.lifetime());
        assertEquals("00A4040008A000000054480001", card.log.get(0), "the applet is selected first");
    }

    @Test
    void followsAMoreDataAnswerWithGetResponse() throws IOException {
        FakeCard card = thaiCard();
        card.useGetResponse = true;
        CardData data = new ThaiIdCardReader().read(card);
        assertEquals("1101700005119", data.nationalId());
        assertTrue(card.log.stream().anyMatch(c -> c.startsWith("00C00000")));
    }

    @Test
    void failsWhenTheCardIsNotAThaiIdCard() {
        FakeCard card = new FakeCard();
        IOException error = assertThrows(IOException.class, () -> new ThaiIdCardReader().read(card));
        assertTrue(error.getMessage().contains("บัตรประชาชน"));
    }

    @Test
    void failsWhenAFieldCannotBeRead() {
        FakeCard card = thaiCard();
        card.answers.remove("80B01579020064");
        assertThrows(IOException.class, () -> new ThaiIdCardReader().read(card));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/id-card-agent":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q -Dtest=ThaiIdCardReaderTest test`
Expected: FAIL (classes missing).

- [ ] **Step 3: Implement**

```java
package com.sabyeguyz.idcard.agent;

import java.io.IOException;

/** One command/response exchange with the card (a PC/SC channel in production, a table in tests). */
interface ApduChannel {
    record Response(byte[] data, int sw) {}

    Response transmit(byte[] command) throws IOException;
}
```

```java
package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Reads the Thai national ID card applet. The read commands are the ones commonly used for this card; confirm them on a
 * real card (see docs/testing for the device check) before relying on them.
 */
final class ThaiIdCardReader {
    private static final byte[] SELECT_APPLET = hex("00A4040008A000000054480001");

    private static final Map<ThaiIdCardParser.Field, byte[]> READ = new EnumMap<>(ThaiIdCardParser.Field.class);

    static {
        READ.put(ThaiIdCardParser.Field.CID, hex("80B00004020 00D".replace(" ", "")));
        READ.put(ThaiIdCardParser.Field.NAME_TH, hex("80B00011020064"));
        READ.put(ThaiIdCardParser.Field.NAME_EN, hex("80B00075020064"));
        READ.put(ThaiIdCardParser.Field.BIRTH, hex("80B000D9020008"));
        READ.put(ThaiIdCardParser.Field.GENDER, hex("80B000E1020001"));
        READ.put(ThaiIdCardParser.Field.ISSUE, hex("80B00167020008"));
        READ.put(ThaiIdCardParser.Field.EXPIRY, hex("80B0016F020008"));
        READ.put(ThaiIdCardParser.Field.ADDRESS, hex("80B01579020064"));
    }

    static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    CardData read(ApduChannel channel) throws IOException {
        ApduChannel.Response selected = channel.transmit(SELECT_APPLET);
        if (!ok(selected.sw())) throw new IOException("ไม่ใช่บัตรประชาชนไทย หรืออ่านแอปเพล็ตของบัตรไม่ได้");
        Map<ThaiIdCardParser.Field, byte[]> raw = new EnumMap<>(ThaiIdCardParser.Field.class);
        for (Map.Entry<ThaiIdCardParser.Field, byte[]> field : READ.entrySet()) {
            raw.put(field.getKey(), readField(channel, field.getValue(), field.getKey()));
        }
        try {
            return ThaiIdCardParser.parse(raw);
        } catch (IllegalArgumentException invalid) {
            throw new IOException(invalid.getMessage(), invalid);
        }
    }

    private static byte[] readField(ApduChannel channel, byte[] command, ThaiIdCardParser.Field field) throws IOException {
        ApduChannel.Response response = channel.transmit(command);
        if ((response.sw() & 0xFF00) == 0x6100) { // more data: ask for it
            response = channel.transmit(new byte[] {0x00, (byte) 0xC0, 0x00, 0x00, (byte) (response.sw() & 0xFF)});
        }
        if (response.sw() != 0x9000) throw new IOException("อ่านข้อมูล " + field + " จากบัตรไม่ได้");
        return response.data();
    }

    private static boolean ok(int sw) {
        return sw == 0x9000 || (sw & 0xFF00) == 0x6100;
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: same command as Step 2. Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add id-card-agent
git commit -m "feat(agent): read the Thai ID card applet with GET RESPONSE handling"
```

---

### Task 8: PC/SC card source

**Files:**
- Create: `.../agent/CardSource.java`, `PcscCardSource.java`
- (No unit test: it needs hardware. It is exercised by `read-once` in Task 10 and the device check in Task 18.)

**Interfaces:**
- Consumes: `ThaiIdCardReader`, `ApduChannel`.
- Produces: `interface CardSource { ReadOutcome readWhenPresent(java.time.Duration wait); }`; `record ReadOutcome(Status status, CardData card, String message)`; `enum Status {OK, NO_CARD, NO_READER, ERROR}` (nested in `CardSource`)

- [ ] **Step 1: Implement**

```java
package com.sabyeguyz.idcard.agent;

import java.time.Duration;

/** Waits for a card at the reader and reads it. */
interface CardSource {
    enum Status { OK, NO_CARD, NO_READER, ERROR }

    record ReadOutcome(Status status, CardData card, String message) {
        static ReadOutcome ok(CardData card) { return new ReadOutcome(Status.OK, card, null); }
        static ReadOutcome of(Status status, String message) { return new ReadOutcome(status, null, message); }
    }

    ReadOutcome readWhenPresent(Duration wait);
}
```

```java
package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/** The system's PC/SC service (winscard on Windows, PCSC.framework on macOS, pcscd on Linux). */
final class PcscCardSource implements CardSource {
    private final ThaiIdCardReader reader = new ThaiIdCardReader();

    @Override
    public ReadOutcome readWhenPresent(Duration wait) {
        List<CardTerminal> terminals;
        try {
            terminals = TerminalFactory.getDefault().terminals().list();
        } catch (CardException | RuntimeException noService) {
            return ReadOutcome.of(Status.NO_READER, "ไม่พบบริการอ่านสมาร์ตการ์ดของระบบ (Linux: ติดตั้ง pcscd)");
        }
        if (terminals.isEmpty()) return ReadOutcome.of(Status.NO_READER, "ไม่พบเครื่องอ่านบัตร");
        long deadline = System.nanoTime() + wait.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                for (CardTerminal terminal : terminals) {
                    if (terminal.isCardPresent()) return readFrom(terminal);
                }
                Thread.sleep(300);
            }
            return ReadOutcome.of(Status.NO_CARD, "ไม่พบบัตร");
        } catch (CardException failure) {
            return ReadOutcome.of(Status.ERROR, "เครื่องอ่านบัตรผิดพลาด");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ReadOutcome.of(Status.ERROR, "ถูกยกเลิก");
        }
    }

    private ReadOutcome readFrom(CardTerminal terminal) {
        try {
            Card card = terminal.connect("*");
            try {
                CardChannel channel = card.getBasicChannel();
                return ReadOutcome.ok(reader.read(command -> {
                    try {
                        ResponseAPDU response = channel.transmit(new CommandAPDU(command));
                        return new ApduChannel.Response(response.getData(), response.getSW());
                    } catch (CardException failure) {
                        throw new IOException("สื่อสารกับบัตรไม่ได้", failure);
                    }
                }));
            } finally {
                card.disconnect(false);
            }
        } catch (IOException | CardException failure) {
            return ReadOutcome.of(Status.ERROR, failure.getMessage() == null ? "อ่านบัตรไม่สำเร็จ" : failure.getMessage());
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `docker run --rm -v "$PWD/id-card-agent":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q -DskipTests compile`
Expected: BUILD SUCCESS (no output with `-q`).

- [ ] **Step 3: Commit**

```bash
git add id-card-agent
git commit -m "feat(agent): read the card through the system PC/SC service"
```

---

### Task 9: Backend client, configuration and the polling loop

**Files:**
- Create: `.../agent/AgentConfig.java`, `BackendClient.java`, `AgentLoop.java`
- Test: `id-card-agent/src/test/java/com/sabyeguyz/idcard/agent/AgentLoopTest.java`

**Interfaces:**
- Consumes: Tasks 6–8.
- Produces:
  - `record AgentConfig(String backendUrl, String token)`; `static AgentConfig load(java.nio.file.Path)`, `void save(java.nio.file.Path)`; `static Path defaultPath()` = `~/.clinic-id-card-agent/config.json`
  - `BackendClient(String backendUrl, String token)`: `Pairing pair(String code)` (static factory `BackendClient.pair(String backendUrl, String code)` returning `AgentConfig`), `Optional<Command> next(int waitSeconds)`, `void sendResult(String requestId, CardSource.ReadOutcome outcome)`; `record Command(String requestId, String purpose)`; throws `IOException` / `UnauthorizedException` (on 401)
  - `AgentLoop(BackendClient client, CardSource source, Duration cardWait)`: `boolean runOnce()` returns `true` when a command was handled; `void run()` loops until `stop()`; `void stop()`; status callback `Consumer<String> onStatus`

- [ ] **Step 1: Write the failing test (a fake backend on the JDK's built-in HTTP server)**

```java
package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentLoopTest {
    HttpServer server;
    String baseUrl;
    final AtomicReference<String> nextResponse = new AtomicReference<>();
    final BlockingQueue<JsonNode> results = new LinkedBlockingQueue<>();
    final AtomicReference<String> lastAuth = new AtomicReference<>();
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void startFakeBackend() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/id-card/agent/next", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = nextResponse.getAndSet(null);
            if (body == null) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.createContext("/api/id-card/agent/result", exchange -> {
            results.add(json.readTree(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/api/id-card/agent/pair", exchange -> {
            JsonNode body = json.readTree(exchange.getRequestBody().readAllBytes());
            boolean good = "ABCD2345".equals(body.path("code").asText());
            byte[] bytes = (good ? "{\"token\":\"tok-1\",\"workstationName\":\"เคาน์เตอร์ 1\",\"branchCode\":\"01\"}" : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(good ? 200 : 400, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeBackend() {
        server.stop(0);
    }

    static CardData card() {
        return new CardData("1101700005119", "นาย", "สมชาย", null, "ใจดี", "Somchai Jaidee", LocalDate.of(1997, 1, 31),
                "M", "88/8", LocalDate.of(2020, 2, 2), null, true);
    }

    @Test
    void doesNothingWhenThereIsNoCommand() throws Exception {
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"), wait -> { throw new AssertionError("no read expected"); }, Duration.ofSeconds(1));
        assertFalse(loop.runOnce());
        assertEquals("Bearer tok-1", lastAuth.get());
        assertTrue(results.isEmpty());
    }

    @Test
    void readsTheCardAndSendsTheResultForACommand() throws Exception {
        nextResponse.set("{\"requestId\":\"r-1\",\"purpose\":\"REGISTER\"}");
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"), wait -> CardSource.ReadOutcome.ok(card()), Duration.ofSeconds(1));
        assertTrue(loop.runOnce());
        JsonNode sent = results.poll(2, TimeUnit.SECONDS);
        assertEquals("r-1", sent.path("requestId").asText());
        assertEquals("OK", sent.path("status").asText());
        assertEquals("1101700005119", sent.path("card").path("nationalId").asText());
        assertEquals("1997-01-31", sent.path("card").path("birthDate").asText());
    }

    @Test
    void reportsWhyTheCardCouldNotBeRead() throws Exception {
        nextResponse.set("{\"requestId\":\"r-2\",\"purpose\":\"LOOKUP\"}");
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"),
                wait -> CardSource.ReadOutcome.of(CardSource.Status.NO_CARD, "ไม่พบบัตร"), Duration.ofSeconds(1));
        loop.runOnce();
        JsonNode sent = results.poll(2, TimeUnit.SECONDS);
        assertEquals("NO_CARD", sent.path("status").asText());
        assertTrue(sent.path("card").isMissingNode() || sent.path("card").isNull());
    }

    @Test
    void pairingExchangesTheCodeForAToken() throws Exception {
        AgentConfig config = BackendClient.pair(baseUrl, "abcd2345");
        assertEquals("tok-1", config.token());
        assertEquals(baseUrl, config.backendUrl());
        assertThrows(java.io.IOException.class, () -> BackendClient.pair(baseUrl, "WRONG"));
    }

    @Test
    void configSurvivesASaveAndLoad() throws Exception {
        var path = java.nio.file.Files.createTempFile("agent-config", ".json");
        new AgentConfig(baseUrl, "tok-1").save(path);
        assertEquals("tok-1", AgentConfig.load(path).token());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/id-card-agent":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q -Dtest=AgentLoopTest test`
Expected: FAIL (classes missing).

- [ ] **Step 3: Implement**

```java
package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Where this counter's agent talks to, and the token it was given when it was paired. */
record AgentConfig(String backendUrl, String token) {
    private static final ObjectMapper JSON = new ObjectMapper();

    static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".clinic-id-card-agent", "config.json");
    }

    static AgentConfig load(Path path) throws IOException {
        return JSON.readValue(path.toFile(), AgentConfig.class);
    }

    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException windows) {
            // no POSIX permissions on Windows: the file stays in the user's profile folder
        }
    }
}
```

```java
package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The agent's calls to the backend. */
final class BackendClient {
    static final class UnauthorizedException extends IOException {
        UnauthorizedException() {
            super("โทเคนของเครื่องอ่านบัตรถูกเพิกถอนหรือไม่ถูกต้อง");
        }
    }

    record Command(String requestId, String purpose) {}

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String backendUrl;
    private final String token;

    BackendClient(String backendUrl, String token) {
        this.backendUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
        this.token = token;
    }

    static AgentConfig pair(String backendUrl, String code) throws IOException {
        String base = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(base + "/api/id-card/agent/pair"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("code", code.trim().toUpperCase()))))
                .build());
        if (response.statusCode() != 200) throw new IOException("จับคู่ไม่สำเร็จ: รหัสไม่ถูกต้องหรือหมดอายุ");
        return new AgentConfig(base, JSON.readTree(response.body()).path("token").asText());
    }

    /** Long-poll: returns after a command arrives or {@code waitSeconds} pass. */
    Optional<Command> next(int waitSeconds) throws IOException {
        HttpResponse<String> response = send(authorized("/api/id-card/agent/next?waitSeconds=" + waitSeconds,
                Duration.ofSeconds(waitSeconds + 15)).GET().build());
        if (response.statusCode() == 401) throw new UnauthorizedException();
        if (response.statusCode() == 204) return Optional.empty();
        if (response.statusCode() != 200) throw new IOException("backend ตอบ " + response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        return Optional.of(new Command(body.path("requestId").asText(), body.path("purpose").asText()));
    }

    void sendResult(String requestId, CardSource.ReadOutcome outcome) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("status", outcome.status().name());
        body.put("message", outcome.message());
        body.put("card", outcome.card());
        HttpResponse<String> response = send(authorized("/api/id-card/agent/result", Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build());
        if (response.statusCode() == 401) throw new UnauthorizedException();
        if (response.statusCode() >= 300) throw new IOException("ส่งผลไม่สำเร็จ: " + response.statusCode());
    }

    private HttpRequest.Builder authorized(String path, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(backendUrl + path)).timeout(timeout).header("Authorization", "Bearer " + token);
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("ถูกยกเลิก", interrupted);
        }
    }
}
```

```java
package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/** Waits for the backend to ask for a card, reads it, and sends back what happened. Nothing from the card is kept or logged. */
final class AgentLoop {
    private static final int POLL_SECONDS = 25;

    private final BackendClient client;
    private final CardSource source;
    private final Duration cardWait;
    private volatile boolean running = true;
    private Consumer<String> onStatus = status -> {};

    AgentLoop(BackendClient client, CardSource source, Duration cardWait) {
        this.client = client;
        this.source = source;
        this.cardWait = cardWait;
    }

    void onStatus(Consumer<String> listener) {
        this.onStatus = listener;
    }

    void stop() {
        running = false;
    }

    /** One poll cycle (used by the loop and by tests). True when a command was handled. */
    boolean runOnce() throws IOException {
        Optional<BackendClient.Command> command = client.next(POLL_SECONDS);
        onStatus.accept("เชื่อมต่อแล้ว");
        if (command.isEmpty()) return false;
        onStatus.accept("กำลังอ่านบัตร");
        client.sendResult(command.get().requestId(), source.readWhenPresent(cardWait));
        onStatus.accept("เชื่อมต่อแล้ว");
        return true;
    }

    void run() {
        long backoffMillis = 1_000;
        while (running) {
            try {
                runOnce();
                backoffMillis = 1_000;
            } catch (BackendClient.UnauthorizedException revoked) {
                onStatus.accept("เครื่องนี้ถูกเพิกถอน ต้องจับคู่ใหม่");
                return;
            } catch (IOException offline) {
                onStatus.accept("เชื่อมต่อ backend ไม่ได้ จะลองใหม่");
                sleep(backoffMillis);
                backoffMillis = Math.min(backoffMillis * 2, 30_000);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: same command as Step 2. Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add id-card-agent
git commit -m "feat(agent): long-poll the backend, read on command and report the outcome"
```

---

### Task 10: Command line, status tray and autostart

**Files:**
- Create: `.../agent/Autostart.java`, `StatusTray.java`, `AgentMain.java`
- Test: `id-card-agent/src/test/java/com/sabyeguyz/idcard/agent/AutostartTest.java`

**Interfaces:**
- Consumes: Tasks 6–9.
- Produces:
  - `AgentMain` commands: `pair <backendUrl> <code>`, `run`, `read-once` (prints the card with the ID number masked, to check a reader), `autostart-install`, `autostart-remove`
  - `Autostart.plist(String javaCommandPath, String jarPath): String`, `Autostart.desktopEntry(String execLine): String`, `Autostart.windowsRunCommand(String exe): String[]`

- [ ] **Step 1: Write the failing test**

```java
package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AutostartTest {
    @Test
    void macLaunchAgentRunsTheAgentAtLogin() {
        String plist = Autostart.plist("/usr/bin/java", "/Applications/ClinicIdCardAgent/id-card-agent.jar");
        assertTrue(plist.contains("<key>RunAtLoad</key>"));
        assertTrue(plist.contains("<string>com.sabyeguyz.idcardagent</string>"));
        assertTrue(plist.contains("<string>/usr/bin/java</string>"));
        assertTrue(plist.contains("<string>run</string>"));
    }

    @Test
    void linuxDesktopEntryStartsTheAgentWithTheSession() {
        String entry = Autostart.desktopEntry("/opt/clinic-id-card-agent/bin/ClinicIdCardAgent run");
        assertTrue(entry.startsWith("[Desktop Entry]"));
        assertTrue(entry.contains("Exec=/opt/clinic-id-card-agent/bin/ClinicIdCardAgent run"));
        assertTrue(entry.contains("X-GNOME-Autostart-enabled=true"));
    }

    @Test
    void windowsUsesTheCurrentUsersRunKey() {
        String[] command = Autostart.windowsRunCommand("C:\\Program Files\\ClinicIdCardAgent\\ClinicIdCardAgent.exe");
        assertEquals("reg", command[0]);
        assertTrue(String.join(" ", command).contains("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"));
        assertTrue(String.join(" ", command).contains("ClinicIdCardAgent.exe"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `docker run --rm -v "$PWD/id-card-agent":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q -Dtest=AutostartTest test`
Expected: FAIL (class missing).

- [ ] **Step 3: Implement `Autostart`**

```java
package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the agent when the user logs in, using each system's own per-user mechanism (no administrator rights). */
final class Autostart {
    private static final String NAME = "ClinicIdCardAgent";

    private Autostart() {}

    static String plist(String javaCommandPath, String jarPath) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>Label</key><string>com.sabyeguyz.idcardagent</string>
                  <key>ProgramArguments</key>
                  <array>
                    <string>%s</string>
                    <string>-jar</string>
                    <string>%s</string>
                    <string>run</string>
                  </array>
                  <key>RunAtLoad</key><true/>
                  <key>KeepAlive</key><true/>
                </dict>
                </plist>
                """.formatted(javaCommandPath, jarPath);
    }

    static String desktopEntry(String execLine) {
        return """
                [Desktop Entry]
                Type=Application
                Name=Clinic ID Card Agent
                Exec=%s
                X-GNOME-Autostart-enabled=true
                """.formatted(execLine);
    }

    static String[] windowsRunCommand(String exe) {
        return new String[] {"reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", NAME, "/t", "REG_SZ", "/d", "\"" + exe + "\" run", "/f"};
    }

    static void install(String launcher) throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            run(windowsRunCommand(launcher));
        } else if (os.contains("mac")) {
            Path file = home.resolve("Library/LaunchAgents/com.sabyeguyz.idcardagent.plist");
            Files.createDirectories(file.getParent());
            Files.writeString(file, plist(System.getProperty("java.home") + "/bin/java", launcher));
        } else {
            Path file = home.resolve(".config/autostart/clinic-id-card-agent.desktop");
            Files.createDirectories(file.getParent());
            Files.writeString(file, desktopEntry(launcher + " run"));
        }
    }

    static void remove() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            run(new String[] {"reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", NAME, "/f"});
        } else if (os.contains("mac")) {
            Files.deleteIfExists(home.resolve("Library/LaunchAgents/com.sabyeguyz.idcardagent.plist"));
        } else {
            Files.deleteIfExists(home.resolve(".config/autostart/clinic-id-card-agent.desktop"));
        }
    }

    private static void run(String[] command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) throw new IOException("คำสั่งตั้งค่าเริ่มอัตโนมัติไม่สำเร็จ");
    }
}
```

- [ ] **Step 4: Implement `StatusTray` and `AgentMain`**

```java
package com.sabyeguyz.idcard.agent;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/** A tray icon whose tooltip shows the agent's state. Where there is no tray (headless Linux) it does nothing. */
final class StatusTray {
    private final TrayIcon icon;

    private StatusTray(TrayIcon icon) {
        this.icon = icon;
    }

    static StatusTray create(Runnable onExit) {
        if (java.awt.GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return new StatusTray(null);
        Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = ((BufferedImage) image).createGraphics();
        g.setColor(new java.awt.Color(0x1E, 0x80, 0x7C));
        g.fillOval(1, 1, 14, 14);
        g.dispose();
        PopupMenu menu = new PopupMenu();
        MenuItem exit = new MenuItem("ปิดโปรแกรมตัวกลาง");
        exit.addActionListener(event -> onExit.run());
        menu.add(exit);
        TrayIcon icon = new TrayIcon(image, "ตัวกลางเครื่องอ่านบัตร", menu);
        try {
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException unavailable) {
            return new StatusTray(null);
        }
        return new StatusTray(icon);
    }

    void status(String text) {
        if (icon != null) icon.setToolTip("ตัวกลางเครื่องอ่านบัตร: " + text);
        System.out.println(text);
    }
}
```

```java
package com.sabyeguyz.idcard.agent;

import java.time.Duration;

public final class AgentMain {
    private AgentMain() {}

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "run" : args[0];
        switch (command) {
            case "pair" -> {
                if (args.length != 3) usage();
                AgentConfig config = BackendClient.pair(args[1], args[2]);
                config.save(AgentConfig.defaultPath());
                System.out.println("จับคู่สำเร็จ บันทึกที่ " + AgentConfig.defaultPath());
            }
            case "run" -> {
                AgentConfig config = AgentConfig.load(AgentConfig.defaultPath());
                AgentLoop loop = new AgentLoop(new BackendClient(config.backendUrl(), config.token()), new PcscCardSource(), Duration.ofSeconds(30));
                StatusTray tray = StatusTray.create(() -> {
                    loop.stop();
                    System.exit(0);
                });
                loop.onStatus(tray::status);
                loop.run();
            }
            case "read-once" -> {
                CardSource.ReadOutcome outcome = new PcscCardSource().readWhenPresent(Duration.ofSeconds(30));
                System.out.println("status=" + outcome.status() + (outcome.message() == null ? "" : " message=" + outcome.message()));
                if (outcome.card() != null) {
                    CardData card = outcome.card();
                    String id = card.nationalId();
                    System.out.println("nationalId=" + id.charAt(0) + "-****-*****-" + id.substring(10, 12) + "-" + id.charAt(12));
                    System.out.println("name=" + card.titleTh() + " " + card.firstNameTh() + " " + card.lastNameTh());
                    System.out.println("birthDate=" + card.birthDate() + " gender=" + card.gender() + " lifetime=" + card.lifetime() + " expiry=" + card.expiryDate());
                    System.out.println("addressLength=" + (card.address() == null ? 0 : card.address().length()));
                }
            }
            case "autostart-install" -> {
                if (args.length != 2) usage();
                Autostart.install(args[1]);
            }
            case "autostart-remove" -> Autostart.remove();
            default -> usage();
        }
    }

    private static void usage() {
        System.err.println("""
                ใช้งาน:
                  pair <backendUrl> <code>        จับคู่เครื่องกับ backend ด้วยรหัสจากผู้จัดการ
                  run                              เริ่มทำงาน (ค่าเริ่มต้น)
                  read-once                        ลองอ่านบัตรหนึ่งครั้งและแสดงผล (ปิดบังเลขบัตร)
                  autostart-install <launcher>     เริ่มอัตโนมัติเมื่อเข้าระบบ
                  autostart-remove                 ยกเลิกการเริ่มอัตโนมัติ""");
        System.exit(2);
    }
}
```

- [ ] **Step 5: Run to verify it passes, then build the jar**

Run: `docker run --rm -v "$PWD/id-card-agent":/w -w /w maven:3.9-eclipse-temurin-17 mvn -q package`
Expected: all agent tests PASS (7 + 4 + 5 + 3 = 19) and `target/id-card-agent.jar` is produced.

- [ ] **Step 6: Commit**

```bash
git add id-card-agent
git commit -m "feat(agent): command line, tray status and per-user autostart"
```

---

### Task 11: Installers (Windows, macOS, Linux)

**Files:**
- Create: `id-card-agent/packaging/build-windows.ps1`, `id-card-agent/packaging/build-unix.sh`, `id-card-agent/packaging/README.md`

jpackage builds a native installer **only for the OS it runs on**, so each script is run once per target OS. No unit test; verification is the install on each OS in Task 18.

- [ ] **Step 1: Write the Windows script**

```powershell
# Builds ClinicIdCardAgent-<version>.msi. Run on Windows with a JDK 17+ and WiX Toolset 3.x on PATH.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
mvn -q package
Pop-Location
jpackage --type msi --name ClinicIdCardAgent --app-version 1.0.0 `
  --input "$root\target" --main-jar id-card-agent.jar --main-class com.sabyeguyz.idcard.agent.AgentMain `
  --add-modules java.smartcardio,java.net.http,java.desktop,java.logging `
  --arguments run --win-shortcut --win-menu --win-per-user-install `
  --dest "$root\target\installer"
Write-Host "Installer: $root\target\installer"
```

- [ ] **Step 2: Write the macOS/Linux script**

```bash
#!/bin/sh
# Builds the installer for the OS it runs on: .pkg on macOS, .deb on Linux (which depends on pcscd).
# Needs a JDK 17+ (jpackage) and, on Linux, fakeroot and dpkg-dev.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
(cd "$ROOT" && mvn -q package)

COMMON="--name ClinicIdCardAgent --app-version 1.0.0 --input $ROOT/target --main-jar id-card-agent.jar \
  --main-class com.sabyeguyz.idcard.agent.AgentMain \
  --add-modules java.smartcardio,java.net.http,java.desktop,java.logging --arguments run --dest $ROOT/target/installer"

case "$(uname -s)" in
  Darwin) jpackage --type pkg $COMMON ;;
  Linux)  jpackage --type deb $COMMON --linux-package-deps pcscd --linux-shortcut ;;
  *) echo "Run packaging/build-windows.ps1 on Windows"; exit 1 ;;
esac
echo "Installer written to $ROOT/target/installer"
```

- [ ] **Step 3: Write the install guide `id-card-agent/packaging/README.md`**

```markdown
# ตัวกลางเครื่องอ่านบัตรประชาชน: ติดตั้ง

1. ผู้จัดการสร้างเครื่องอ่านบัตรในระบบ (หน้า "เครื่องอ่านบัตร") แล้วจดรหัสจับคู่ 8 ตัวอักษร (ใช้ได้ 10 นาที ครั้งเดียว)
2. ติดตั้งตัวติดตั้งของระบบปฏิบัติการนั้น (`.msi` / `.pkg` / `.deb`)
   - Windows: ถ้าขึ้น SmartScreen เตือนเพราะโปรแกรมยังไม่ได้ลงลายเซ็น ให้เลือก "More info" แล้ว "Run anyway"
   - macOS: ถ้า Gatekeeper ปฏิเสธ ให้คลิกขวาที่ตัวติดตั้งแล้วเลือก Open
   - Linux: `sudo apt install ./ClinicIdCardAgent_1.0.0_amd64.deb` (จะติดตั้ง `pcscd` ให้อัตโนมัติ)
3. จับคู่ ที่เครื่องหน้าเคาน์เตอร์ (เปลี่ยน URL ให้ตรงกับ backend ของคลินิก):

       ClinicIdCardAgent pair https://<backend-url> ABCD2345

4. ตั้งให้เริ่มอัตโนมัติ: `ClinicIdCardAgent autostart-install <พาธของโปรแกรม>`
5. เสียบเครื่องอ่านและบัตรแล้วลอง `ClinicIdCardAgent read-once` ควรเห็นชื่อและเลขบัตรที่ถูกปิดบัง
6. เปิดโปรแกรม `ClinicIdCardAgent run` ไอคอนที่ถาดระบบแสดงสถานะ
```

- [ ] **Step 3: Commit**

```bash
git add id-card-agent/packaging
git commit -m "feat(agent): installer scripts for Windows, macOS and Linux"
```

---

## Part C — Frontend (`feat/id-card-reader-frontend`)

### Task 12: Map card data onto the registration form

**Files:**
- Create: `frontend/src/features/idcard/cardToForm.ts`
- Test: `frontend/src/features/idcard/cardToForm.spec.ts`
- Modify: `frontend/src/api/client.ts` (add `IdCardData` type, see Task 13 — add the type now)

**Interfaces:**
- Produces:
  - `type IdCardData` (exported from `@/api/client`): `{ nationalId: string; titleTh: string | null; firstNameTh: string | null; middleNameTh: string | null; lastNameTh: string | null; fullNameEn: string | null; birthDate: string | null; gender: 'M' | 'F' | null; address: string | null; issueDate: string | null; expiryDate: string | null; lifetime: boolean }`
  - `mapCardToRegistration(card: IdCardData): Partial<PatientRegistrationForm>`
  - `cardExpired(card: IdCardData, today: string): boolean`

- [ ] **Step 1: Add the type to `frontend/src/api/client.ts`** (append near the other exported types)

```ts
export type IdCardData = {
  nationalId: string
  titleTh: string | null
  firstNameTh: string | null
  middleNameTh: string | null
  lastNameTh: string | null
  fullNameEn: string | null
  birthDate: string | null
  gender: 'M' | 'F' | null
  address: string | null
  issueDate: string | null
  expiryDate: string | null
  lifetime: boolean
}
```

- [ ] **Step 2: Write the failing test**

```ts
import { describe, expect, it } from 'vitest'
import type { IdCardData } from '@/api/client'
import { cardExpired, mapCardToRegistration } from './cardToForm'

const card = (over: Partial<IdCardData> = {}): IdCardData => ({
  nationalId: '1101700005119',
  titleTh: 'นาย',
  firstNameTh: 'สมชาย',
  middleNameTh: null,
  lastNameTh: 'ใจดี',
  fullNameEn: 'Somchai Jaidee',
  birthDate: '1997-01-31',
  gender: 'M',
  address: '88/8 หมู่ที่ 3 ตำบลจรเข้บัว',
  issueDate: '2020-02-02',
  expiryDate: null,
  lifetime: true,
  ...over,
})

describe('mapCardToRegistration', () => {
  it('fills the identity, names, birth date, gender and address from the card', () => {
    expect(mapCardToRegistration(card())).toEqual({
      identityDocumentType: 'NATIONAL_ID',
      identityNumber: '1101700005119',
      nationality: 'ไทย',
      title: 'นาย',
      firstName: 'สมชาย',
      middleName: '',
      lastName: 'ใจดี',
      fullNameEnglish: 'Somchai Jaidee',
      birthDate: '1997-01-31',
      gender: 'ชาย',
      address: '88/8 หมู่ที่ 3 ตำบลจรเข้บัว',
    })
  })

  it('maps the common Thai title spellings to the five titles the system supports', () => {
    expect(mapCardToRegistration(card({ titleTh: 'น.ส.' })).title).toBe('นางสาว')
    expect(mapCardToRegistration(card({ titleTh: 'นางสาว' })).title).toBe('นางสาว')
    expect(mapCardToRegistration(card({ titleTh: 'นาง' })).title).toBe('นาง')
    expect(mapCardToRegistration(card({ titleTh: 'ด.ช.' })).title).toBe('เด็กชาย')
    expect(mapCardToRegistration(card({ titleTh: 'เด็กหญิง' })).title).toBe('เด็กหญิง')
  })

  it('leaves the title for the staff to choose when the card has another title', () => {
    expect('title' in mapCardToRegistration(card({ titleTh: 'ดร.' }))).toBe(false)
    expect('title' in mapCardToRegistration(card({ titleTh: null }))).toBe(false)
  })

  it('takes the gender from the card, not from the title', () => {
    expect(mapCardToRegistration(card({ titleTh: 'นาย', gender: 'F' })).gender).toBe('หญิง')
    expect('gender' in mapCardToRegistration(card({ gender: null }))).toBe(false)
  })

  it('keeps a middle name and skips fields the card did not give', () => {
    const mapped = mapCardToRegistration(card({ middleNameTh: 'กลาง', fullNameEn: null, address: null, birthDate: null }))
    expect(mapped.middleName).toBe('กลาง')
    expect('fullNameEnglish' in mapped).toBe(false)
    expect('address' in mapped).toBe(false)
    expect('birthDate' in mapped).toBe(false)
  })
})

describe('cardExpired', () => {
  it('is never expired for a lifetime card', () => {
    expect(cardExpired(card({ lifetime: true, expiryDate: null }), '2030-01-01')).toBe(false)
  })

  it('is expired once the expiry date is before today', () => {
    expect(cardExpired(card({ lifetime: false, expiryDate: '2025-12-31' }), '2026-11-10')).toBe(true)
    expect(cardExpired(card({ lifetime: false, expiryDate: '2026-11-10' }), '2026-11-10')).toBe(false)
  })
})
```

- [ ] **Step 3: Run to verify it fails**

Run (from `frontend/`): `npx vitest run src/features/idcard/cardToForm.spec.ts`
Expected: FAIL (module not found).

- [ ] **Step 4: Implement**

```ts
import type { IdCardData } from '@/api/client'
import type { PatientRegistrationForm } from '@/features/patients/data/patientRegistration'

// The five titles the system supports (title_enum); anything else is left for the staff to choose.
const TITLES: Record<string, string> = {
  นาย: 'นาย',
  นาง: 'นาง',
  นางสาว: 'นางสาว',
  'น.ส.': 'นางสาว',
  เด็กชาย: 'เด็กชาย',
  'ด.ช.': 'เด็กชาย',
  เด็กหญิง: 'เด็กหญิง',
  'ด.ญ.': 'เด็กหญิง',
}

export function mapCardToRegistration(card: IdCardData): Partial<PatientRegistrationForm> {
  const form: Partial<PatientRegistrationForm> = {
    identityDocumentType: 'NATIONAL_ID',
    identityNumber: card.nationalId,
    nationality: 'ไทย',
  }
  const title = card.titleTh ? TITLES[card.titleTh.trim()] : undefined
  if (title) form.title = title
  if (card.firstNameTh) form.firstName = card.firstNameTh
  form.middleName = card.middleNameTh ?? ''
  if (card.lastNameTh) form.lastName = card.lastNameTh
  if (card.fullNameEn) form.fullNameEnglish = card.fullNameEn
  if (card.birthDate) form.birthDate = card.birthDate
  if (card.gender === 'M') form.gender = 'ชาย'
  if (card.gender === 'F') form.gender = 'หญิง'
  if (card.address) form.address = card.address
  return form
}

export function cardExpired(card: IdCardData, today: string): boolean {
  return !card.lifetime && !!card.expiryDate && card.expiryDate < today
}
```

- [ ] **Step 5: Run to verify it passes**

Run: same command. Expected: 7 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/client.ts frontend/src/features/idcard
git commit -m "feat(idcard): map the card's data onto the registration form"
```

---

### Task 13: API client and the reader composable

**Files:**
- Modify: `frontend/src/api/client.ts` (append `idCardApi` and its types)
- Create: `frontend/src/features/idcard/useIdCardReader.ts`
- Test: `frontend/src/features/idcard/useIdCardReader.spec.ts`

**Interfaces:**
- Consumes: Task 12 `IdCardData`; `api.get/post` from `client.ts`.
- Produces:
  - Types: `IdCardWorkstation {id, name, branchCode, online, status}`, `IdCardReadPurpose`, `IdCardReadView`, `IdCardLookup`
  - `idCardApi.workstations(branchCode?)`, `.start(workstationId, purpose)`, `.result(id)`, `.createWorkstation(name, branchCode)`, `.newPairingCode(id)`, `.revokeWorkstation(id)`
  - `useIdCardReader(options?)` returning `{ workstations, selectedId, state, message, load(branchCode?), select(id), read(purpose), cancel() }` where `state` is `'idle' | 'waiting' | 'done' | 'failed'`; `read` resolves with `IdCardReadView` (status `DONE`) or `null` on failure/cancel
  - options: `{ api?, intervalMs? (700), timeoutMs? (50000) }` for tests

- [ ] **Step 1: Append to `client.ts`**

```ts
export type IdCardWorkstation = {
  id: number
  name: string
  branchCode: string
  online: boolean
  status: 'Active' | 'Revoked'
}
export type IdCardReadPurpose = 'REGISTER' | 'LOOKUP'
export type IdCardLookup = {
  exists: boolean
  patient: { id: number; hn: string; fullName: string } | null
}
export type IdCardReadView = {
  status: 'PENDING' | 'DONE' | 'FAILED'
  outcome?: string
  message?: string
  card?: IdCardData
  lookup?: IdCardLookup
}
export type IdCardWorkstationCreated = {
  id: number
  name: string
  branchCode: string
  pairingCode: string
  pairingExpiresAt: string
}

export const idCardApi = {
  workstations: (branchCode?: string) =>
    api.get<IdCardWorkstation[]>(
      `/api/id-card/workstations${branchCode ? `?branchCode=${encodeURIComponent(branchCode)}` : ''}`,
    ),
  start: (workstationId: number, purpose: IdCardReadPurpose) =>
    api.post<{ id: string; expiresAt: string }>('/api/id-card/reads', { workstationId, purpose }),
  result: (id: string) => api.get<IdCardReadView>(`/api/id-card/reads/${encodeURIComponent(id)}`),
  createWorkstation: (name: string, branchCode: string) =>
    api.post<IdCardWorkstationCreated>('/api/id-card/workstations', { name, branchCode }),
  newPairingCode: (id: number) =>
    api.post<IdCardWorkstationCreated>(`/api/id-card/workstations/${id}/pairing-code`, {}),
  revokeWorkstation: (id: number) => api.post<void>(`/api/id-card/workstations/${id}/revoke`, {}),
}
```

- [ ] **Step 2: Write the failing test**

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type IdCardReadView, type IdCardWorkstation } from '@/api/client'
import { useIdCardReader } from './useIdCardReader'

const ws = (over: Partial<IdCardWorkstation> = {}): IdCardWorkstation => ({
  id: 1,
  name: 'เคาน์เตอร์ 1',
  branchCode: '01',
  online: true,
  status: 'Active',
  ...over,
})

function fakeApi(results: IdCardReadView[], workstations = [ws()]) {
  const queue = [...results]
  return {
    workstations: vi.fn().mockResolvedValue(workstations),
    start: vi.fn().mockResolvedValue({ id: 'r1', expiresAt: '2026-11-10T10:01:00Z' }),
    result: vi.fn().mockImplementation(async () => queue.shift() ?? { status: 'PENDING' }),
  }
}

describe('useIdCardReader', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
  })
  afterEach(() => vi.useRealTimers())

  it('selects the only online reader on its own', async () => {
    const reader = useIdCardReader({ api: fakeApi([]) as never })
    await reader.load('01')
    expect(reader.selectedId.value).toBe(1)
  })

  it('polls until the card arrives and resolves with it', async () => {
    const api = fakeApi([{ status: 'PENDING' }, { status: 'DONE', card: { nationalId: '1101700005119' } as never }])
    const reader = useIdCardReader({ api: api as never, intervalMs: 700 })
    await reader.load('01')
    const done = reader.read('REGISTER')
    expect(reader.state.value).toBe('waiting')
    await vi.advanceTimersByTimeAsync(1500)
    const view = await done
    expect(view?.card?.nationalId).toBe('1101700005119')
    expect(reader.state.value).toBe('done')
    expect(api.start).toHaveBeenCalledWith(1, 'REGISTER')
  })

  it('reports a failed read with a Thai message', async () => {
    const api = fakeApi([{ status: 'FAILED', outcome: 'NO_CARD', message: 'ไม่พบบัตร ใส่บัตรแล้วกดอ่านใหม่' }])
    const reader = useIdCardReader({ api: api as never })
    await reader.load('01')
    const done = reader.read('LOOKUP')
    await vi.advanceTimersByTimeAsync(800)
    expect(await done).toBeNull()
    expect(reader.state.value).toBe('failed')
    expect(reader.message.value).toBe('ไม่พบบัตร ใส่บัตรแล้วกดอ่านใหม่')
  })

  it('says so when the counter program is offline (409) or too many reads (429)', async () => {
    const api = fakeApi([])
    api.start.mockRejectedValueOnce(new ApiError('offline', 409))
    const reader = useIdCardReader({ api: api as never })
    await reader.load('01')
    expect(await reader.read('REGISTER')).toBeNull()
    expect(reader.message.value).toContain('ออฟไลน์')

    api.start.mockRejectedValueOnce(new ApiError('slow down', 429))
    expect(await reader.read('REGISTER')).toBeNull()
    expect(reader.message.value).toContain('บ่อยเกินไป')
  })

  it('gives up after the timeout', async () => {
    const reader = useIdCardReader({ api: fakeApi([]) as never, intervalMs: 700, timeoutMs: 3000 })
    await reader.load('01')
    const done = reader.read('REGISTER')
    await vi.advanceTimersByTimeAsync(4000)
    expect(await done).toBeNull()
    expect(reader.state.value).toBe('failed')
    expect(reader.message.value).toContain('หมดเวลา')
  })

  it('stops polling when cancelled', async () => {
    const api = fakeApi([])
    const reader = useIdCardReader({ api: api as never, intervalMs: 700 })
    await reader.load('01')
    const done = reader.read('REGISTER')
    await vi.advanceTimersByTimeAsync(800)
    reader.cancel()
    await vi.advanceTimersByTimeAsync(5000)
    expect(await done).toBeNull()
    expect(reader.state.value).toBe('idle')
    const calls = api.result.mock.calls.length
    await vi.advanceTimersByTimeAsync(5000)
    expect(api.result.mock.calls.length).toBe(calls)
  })

  it('needs a reader to be selected first', async () => {
    const reader = useIdCardReader({ api: fakeApi([], []) as never })
    await reader.load('01')
    expect(await reader.read('REGISTER')).toBeNull()
    expect(reader.message.value).toContain('เครื่องอ่านบัตร')
  })

  it('remembers the chosen reader in the browser', async () => {
    const reader = useIdCardReader({ api: fakeApi([], [ws(), ws({ id: 2, name: 'เคาน์เตอร์ 2' })]) as never })
    await reader.load('01')
    expect(reader.selectedId.value).toBeNull()
    reader.select(2)
    expect(localStorage.getItem('idCardWorkstationId')).toBe('2')
    const again = useIdCardReader({ api: fakeApi([], [ws(), ws({ id: 2, name: 'เคาน์เตอร์ 2' })]) as never })
    await again.load('01')
    expect(again.selectedId.value).toBe(2)
  })
})
```

- [ ] **Step 3: Run to verify it fails**

Run: `npx vitest run src/features/idcard/useIdCardReader.spec.ts`
Expected: FAIL (module not found).

- [ ] **Step 4: Implement**

```ts
import { ref } from 'vue'
import { ApiError, idCardApi, type IdCardReadPurpose, type IdCardReadView, type IdCardWorkstation } from '@/api/client'

type Api = Pick<typeof idCardApi, 'workstations' | 'start' | 'result'>
type State = 'idle' | 'waiting' | 'done' | 'failed'

const STORAGE_KEY = 'idCardWorkstationId'

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

function stored(): number | null {
  try {
    const value = Number(localStorage.getItem(STORAGE_KEY))
    return Number.isFinite(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

export function useIdCardReader(options: { api?: Api; intervalMs?: number; timeoutMs?: number } = {}) {
  const api = options.api ?? idCardApi
  const intervalMs = options.intervalMs ?? 700
  const timeoutMs = options.timeoutMs ?? 50_000

  const workstations = ref<IdCardWorkstation[]>([])
  const selectedId = ref<number | null>(null)
  const state = ref<State>('idle')
  const message = ref('')
  let generation = 0

  async function load(branchCode?: string) {
    try {
      workstations.value = await api.workstations(branchCode)
    } catch {
      workstations.value = []
    }
    const online = workstations.value.filter((w) => w.online)
    const remembered = stored()
    if (remembered && workstations.value.some((w) => w.id === remembered)) selectedId.value = remembered
    else if (online.length === 1) selectedId.value = online[0].id
    else if (workstations.value.length === 1) selectedId.value = workstations.value[0].id
    else selectedId.value = null
  }

  function select(id: number) {
    selectedId.value = id
    try {
      localStorage.setItem(STORAGE_KEY, String(id))
    } catch {
      // the choice is only a convenience
    }
  }

  function cancel() {
    generation++
    state.value = 'idle'
    message.value = ''
  }

  function fail(text: string): null {
    state.value = 'failed'
    message.value = text
    return null
  }

  async function read(purpose: IdCardReadPurpose): Promise<IdCardReadView | null> {
    const mine = ++generation
    message.value = ''
    if (selectedId.value === null) {
      return fail('ยังไม่ได้เลือกเครื่องอ่านบัตร หรือยังไม่มีเครื่องอ่านบัตรของสาขานี้')
    }
    state.value = 'waiting'
    let id: string
    try {
      id = (await api.start(selectedId.value, purpose)).id
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        return fail('โปรแกรมตัวกลางของเครื่องนี้ออฟไลน์ ตรวจว่าเปิดโปรแกรมที่เคาน์เตอร์แล้ว')
      }
      if (error instanceof ApiError && error.status === 429) return fail('สั่งอ่านบัตรบ่อยเกินไป รอสักครู่แล้วลองใหม่')
      if (error instanceof ApiError && error.status === 403) return fail('เครื่องอ่านบัตรนี้อยู่คนละสาขากับคุณ')
      return fail(error instanceof ApiError ? error.message : 'สั่งอ่านบัตรไม่สำเร็จ')
    }
    const deadline = Date.now() + timeoutMs
    while (mine === generation) {
      await sleep(intervalMs)
      if (mine !== generation) return null
      let view: IdCardReadView
      try {
        view = await api.result(id)
      } catch (error) {
        if (mine !== generation) return null
        return fail(error instanceof ApiError && error.status === 404 ? 'หมดเวลา ไม่ได้รับข้อมูลจากเครื่องอ่านบัตร' : 'ติดต่อระบบไม่ได้')
      }
      if (mine !== generation) return null
      if (view.status === 'DONE') {
        state.value = 'done'
        return view
      }
      if (view.status === 'FAILED') return fail(view.message ?? 'อ่านบัตรไม่สำเร็จ')
      if (Date.now() >= deadline) return fail('หมดเวลา ไม่ได้รับข้อมูลจากเครื่องอ่านบัตร')
    }
    return null
  }

  return { workstations, selectedId, state, message, load, select, read, cancel }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: same command. Expected: 8 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/client.ts frontend/src/features/idcard
git commit -m "feat(idcard): start a card read and poll for its one-time result"
```

---

### Task 14: "อ่านบัตร" button, registration and search

**Files:**
- Create: `frontend/src/features/idcard/IdCardReadButton.vue`, `frontend/src/features/idcard/IdCardReadButton.spec.ts`
- Modify: `frontend/src/features/patients/views/PatientRegistrationView.vue`, `frontend/src/features/patients/views/PatientSearchView.vue`

**Interfaces:**
- Consumes: Tasks 12–13.
- Produces: `<IdCardReadButton purpose="REGISTER" :branch-code="..." @card="(card: IdCardData) => void" />` and `<IdCardReadButton purpose="LOOKUP" @lookup="(result: IdCardLookup) => void" />`

- [ ] **Step 1: Write the failing component test**

```ts
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const read = vi.fn()
const reader = {
  workstations: ref([{ id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' }]),
  selectedId: ref<number | null>(1),
  state: ref('idle'),
  message: ref(''),
  load: vi.fn().mockResolvedValue(undefined),
  select: vi.fn(),
  read,
  cancel: vi.fn(),
}
vi.mock('./useIdCardReader', () => ({ useIdCardReader: () => reader }))

import IdCardReadButton from './IdCardReadButton.vue'

describe('IdCardReadButton', () => {
  beforeEach(() => {
    read.mockReset()
    reader.state.value = 'idle'
    reader.message.value = ''
    reader.selectedId.value = 1
    reader.workstations.value = [{ id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' }]
  })

  it('emits the card for a REGISTER read', async () => {
    read.mockResolvedValue({ status: 'DONE', card: { nationalId: '1101700005119' } })
    const wrapper = mount(IdCardReadButton, { props: { purpose: 'REGISTER', branchCode: '01' } })
    await wrapper.get('button.id-card-read').trigger('click')
    await flushPromises()
    expect(read).toHaveBeenCalledWith('REGISTER')
    expect(wrapper.emitted('card')?.[0][0]).toEqual({ nationalId: '1101700005119' })
  })

  it('emits the lookup result for a LOOKUP read', async () => {
    read.mockResolvedValue({ status: 'DONE', lookup: { exists: true, patient: { id: 5, hn: 'HN0005', fullName: 'สมชาย ใจดี' } } })
    const wrapper = mount(IdCardReadButton, { props: { purpose: 'LOOKUP' } })
    await wrapper.get('button.id-card-read').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('lookup')?.[0][0]).toMatchObject({ exists: true })
  })

  it('shows why a read failed and lets the staff type by hand', async () => {
    read.mockImplementation(async () => {
      reader.state.value = 'failed'
      reader.message.value = 'ไม่พบบัตร ใส่บัตรแล้วกดอ่านใหม่'
      return null
    })
    const wrapper = mount(IdCardReadButton, { props: { purpose: 'REGISTER' } })
    await wrapper.get('button.id-card-read').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('ไม่พบบัตร ใส่บัตรแล้วกดอ่านใหม่')
    expect(wrapper.emitted('card')).toBeUndefined()
  })

  it('asks which reader to use when there are several and none is chosen', () => {
    reader.selectedId.value = null
    reader.workstations.value = [
      { id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' },
      { id: 2, name: 'เคาน์เตอร์ 2', branchCode: '01', online: false, status: 'Active' },
    ]
    const wrapper = mount(IdCardReadButton, { props: { purpose: 'REGISTER' } })
    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.get('button.id-card-read').attributes('disabled')).toBeDefined()
  })

  it('shows a hint when the branch has no card reader yet', () => {
    reader.selectedId.value = null
    reader.workstations.value = []
    const wrapper = mount(IdCardReadButton, { props: { purpose: 'REGISTER' } })
    expect(wrapper.text()).toContain('ยังไม่มีเครื่องอ่านบัตร')
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/features/idcard/IdCardReadButton.spec.ts`
Expected: FAIL (component missing).

- [ ] **Step 3: Implement the component**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import type { IdCardData, IdCardLookup, IdCardReadPurpose } from '@/api/client'
import { useIdCardReader } from './useIdCardReader'

const props = defineProps<{ purpose: IdCardReadPurpose; branchCode?: string }>()
const emit = defineEmits<{
  (event: 'card', card: IdCardData): void
  (event: 'lookup', result: IdCardLookup): void
}>()

const reader = useIdCardReader()
const authUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null
  } catch {
    return null
  }
})
const isManager = computed(() => authUser.value?.role === 'MANAGER')
const choices = computed(() => reader.workstations.value.filter((w) => w.status === 'Active'))
const busy = computed(() => reader.state.value === 'waiting')
const ready = computed(() => reader.selectedId.value !== null && !busy.value)

onMounted(() => reader.load(props.branchCode || undefined))
onBeforeUnmount(() => reader.cancel())

async function readCard() {
  const view = await reader.read(props.purpose)
  if (!view) return
  if (props.purpose === 'REGISTER' && view.card) emit('card', view.card)
  if (props.purpose === 'LOOKUP' && view.lookup) emit('lookup', view.lookup)
}

function chooseReader(event: Event) {
  const id = Number((event.target as HTMLSelectElement).value)
  if (id) reader.select(id)
}
</script>

<template>
  <div class="id-card-read">
    <select v-if="choices.length > 1" :value="reader.selectedId.value ?? ''" aria-label="เลือกเครื่องอ่านบัตร" @change="chooseReader">
      <option value="" disabled>เลือกเครื่องอ่านบัตร</option>
      <option v-for="w in choices" :key="w.id" :value="w.id">{{ w.name }}{{ w.online ? '' : ' (ออฟไลน์)' }}</option>
    </select>
    <button type="button" class="id-card-read secondary" :disabled="!ready" @click="readCard">
      {{ busy ? 'รอบัตร… เสียบบัตรที่เครื่องอ่าน' : 'อ่านบัตรประชาชน' }}
    </button>
    <button v-if="busy" type="button" class="secondary" @click="reader.cancel()">ยกเลิก</button>
    <small v-if="choices.length === 0" class="id-card-hint">
      ยังไม่มีเครื่องอ่านบัตรของสาขานี้ พิมพ์ข้อมูลเองได้
      <RouterLink v-if="isManager" to="/staff/settings/card-readers">จัดการเครื่องอ่านบัตร</RouterLink>
    </small>
    <small v-if="reader.state.value === 'failed'" role="alert" class="required-error">{{ reader.message.value }}</small>
  </div>
</template>

<style scoped>
.id-card-read {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.id-card-hint {
  color: #6b7d76;
}
</style>
```

- [ ] **Step 4: Run to verify it passes**

Run: same command. Expected: 5 tests PASS.

- [ ] **Step 5: Wire it into the registration view**

In `PatientRegistrationView.vue` `<script setup>`, add imports and the handler (after the existing imports and `form` definition):

```ts
import IdCardReadButton from '@/features/idcard/IdCardReadButton.vue'
import { cardExpired, mapCardToRegistration } from '@/features/idcard/cardToForm'
import type { IdCardData } from '@/api/client'
```

```ts
const cardExpiredWarning = ref('')
function applyCard(card: IdCardData) {
  Object.assign(form, mapCardToRegistration(card))
  cardExpiredWarning.value = cardExpired(card, clinicToday()) ? 'บัตรประชาชนใบนี้หมดอายุแล้ว ตรวจสอบกับผู้ป่วยก่อนบันทึก' : ''
}
```

In the template, directly after the identity-number `</label>` (the one whose text is `{{ isForeign ? 'เลขพาสปอร์ต' : 'เลขบัตรประชาชน' }}`), insert:

```vue
              <div v-if="!isForeign" class="id-card-row">
                <IdCardReadButton purpose="REGISTER" :branch-code="form.branchCode" @card="applyCard" />
                <small v-if="cardExpiredWarning" role="status" class="required-error">{{ cardExpiredWarning }}</small>
              </div>
```

- [ ] **Step 6: Wire it into the search view**

In `PatientSearchView.vue` `<script setup>`, add:

```ts
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import IdCardReadButton from '@/features/idcard/IdCardReadButton.vue'
import type { IdCardLookup } from '@/api/client'

const router = useRouter()
const cardNotice = ref('')
function onCardLookup(result: IdCardLookup) {
  if (result.exists && result.patient) {
    cardNotice.value = ''
    router.push(`/staff/patients/${result.patient.id}`)
  } else {
    cardNotice.value = 'ไม่พบผู้ป่วยที่ใช้บัตรใบนี้ ลงทะเบียนผู้ป่วยใหม่ได้'
  }
}
```

In the template, inside `<section class="page-heading"><div>` directly after the "ลงทะเบียนผู้ป่วยใหม่" `<RouterLink>`, insert:

```vue
          <IdCardReadButton purpose="LOOKUP" @lookup="onCardLookup" />
          <p v-if="cardNotice" role="status">
            {{ cardNotice }} <RouterLink to="/staff/patients/new">+ ลงทะเบียนผู้ป่วยใหม่</RouterLink>
          </p>
```

- [ ] **Step 7: Run the affected existing specs**

Run: `npx vitest run src/features/patients src/features/idcard`
Expected: the new specs PASS; no *new* failures in the patients specs (compare to the failures that existed before the change). `npx vue-tsc --noEmit -p tsconfig.app.json` prints nothing.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/features/idcard frontend/src/features/patients
git commit -m "feat(idcard): read the card from the registration and search pages"
```

---

### Task 15: Manager page for card readers

**Files:**
- Create: `frontend/src/features/idcard/CardReadersView.vue`, `frontend/src/features/idcard/CardReadersView.spec.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Consumes: `idCardApi` (Task 13), `selectedManagerBranch` from `@/features/staff/data/managerBranch`.
- Produces: route `/staff/settings/card-readers` (name `staff-card-readers`, `meta: { roles: ['manager'] }`)

- [ ] **Step 1: Write the failing test**

```ts
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/client')>()),
  idCardApi: {
    workstations: vi.fn(),
    createWorkstation: vi.fn(),
    newPairingCode: vi.fn(),
    revokeWorkstation: vi.fn(),
  },
}))
vi.mock('@/features/staff/data/managerBranch', () => ({
  selectedManagerBranch: () => ({ code: '01', name: 'สาขาพระราม 9' }),
}))
vi.mock('@/features/staff/components/StaffHeader.vue', () => ({ default: { template: '<header />' } }))

import { idCardApi } from '@/api/client'
import CardReadersView from './CardReadersView.vue'

const api = vi.mocked(idCardApi)

describe('CardReadersView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.workstations.mockResolvedValue([
      { id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' },
      { id: 2, name: 'เก่า', branchCode: '01', online: false, status: 'Revoked' },
    ])
  })

  it('lists the readers with their state', async () => {
    const wrapper = mount(CardReadersView)
    await flushPromises()
    expect(wrapper.text()).toContain('เคาน์เตอร์ 1')
    expect(wrapper.text()).toContain('ออนไลน์')
    expect(wrapper.text()).toContain('เพิกถอนแล้ว')
  })

  it('adds a reader and shows its pairing code once', async () => {
    api.createWorkstation.mockResolvedValue({ id: 3, name: 'เคาน์เตอร์ 2', branchCode: '01', pairingCode: 'ABCD2345', pairingExpiresAt: '2026-11-10T10:10:00Z' })
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('input[name="name"]').setValue('เคาน์เตอร์ 2')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.createWorkstation).toHaveBeenCalledWith('เคาน์เตอร์ 2', '01')
    expect(wrapper.text()).toContain('ABCD2345')
  })

  it('revokes a reader after confirming', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.revokeWorkstation.mockResolvedValue(undefined)
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('button.revoke').trigger('click')
    await flushPromises()
    expect(api.revokeWorkstation).toHaveBeenCalledWith(1)
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/features/idcard/CardReadersView.spec.ts`
Expected: FAIL (component missing).

- [ ] **Step 3: Implement**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { ApiError, idCardApi, type IdCardWorkstation, type IdCardWorkstationCreated } from '@/api/client'

const workstations = ref<IdCardWorkstation[]>([])
const name = ref('')
const created = ref<IdCardWorkstationCreated | null>(null)
const message = ref('')
const branchCode = computed(() => selectedManagerBranch()?.code ?? '')

async function load() {
  try {
    workstations.value = await idCardApi.workstations(branchCode.value || undefined)
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'โหลดรายการเครื่องอ่านบัตรไม่สำเร็จ'
  }
}

async function add() {
  message.value = ''
  if (!name.value.trim() || !branchCode.value) {
    message.value = 'กรอกชื่อเครื่อง และเลือกสาขาก่อน'
    return
  }
  try {
    created.value = await idCardApi.createWorkstation(name.value.trim(), branchCode.value)
    name.value = ''
    await load()
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'เพิ่มเครื่องอ่านบัตรไม่สำเร็จ'
  }
}

async function repair(id: number) {
  created.value = await idCardApi.newPairingCode(id)
}

async function revoke(workstation: IdCardWorkstation) {
  if (!window.confirm(`เพิกถอนเครื่อง "${workstation.name}"? โปรแกรมตัวกลางของเครื่องนี้จะใช้งานไม่ได้อีก`)) return
  await idCardApi.revokeWorkstation(workstation.id)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="patient-workspace">
    <StaffHeader />
    <main>
      <section class="page-heading">
        <div>
          <h1>เครื่องอ่านบัตรประชาชน</h1>
          <p>เพิ่มเครื่องอ่านบัตรของสาขา แล้วนำรหัสจับคู่ไปใส่ในโปรแกรมตัวกลางที่เครื่องหน้าเคาน์เตอร์</p>
        </div>
      </section>

      <form class="card-reader-add" @submit.prevent="add">
        <label>
          ชื่อเครื่อง
          <input v-model="name" name="name" maxlength="100" placeholder="เช่น เคาน์เตอร์ 1" />
        </label>
        <button type="submit" class="primary">เพิ่มเครื่อง</button>
      </form>
      <p v-if="message" role="alert" class="required-error">{{ message }}</p>

      <section v-if="created" class="pairing-code" role="status">
        <p>รหัสจับคู่ของ "{{ created.name }}" (ใช้ได้ครั้งเดียว ภายใน 10 นาที)</p>
        <strong>{{ created.pairingCode }}</strong>
      </section>

      <table class="card-reader-list">
        <thead>
          <tr><th>ชื่อ</th><th>สาขา</th><th>สถานะ</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="w in workstations" :key="w.id">
            <td>{{ w.name }}</td>
            <td>{{ w.branchCode }}</td>
            <td>{{ w.status === 'Revoked' ? 'เพิกถอนแล้ว' : w.online ? 'ออนไลน์' : 'ออฟไลน์' }}</td>
            <td>
              <template v-if="w.status === 'Active'">
                <button type="button" class="secondary" @click="repair(w.id)">รหัสจับคู่ใหม่</button>
                <button type="button" class="secondary revoke" @click="revoke(w)">เพิกถอน</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </main>
  </div>
</template>

<style scoped>
.card-reader-add {
  display: flex;
  gap: 12px;
  align-items: end;
  margin: 16px 0;
}
.pairing-code strong {
  font-size: 32px;
  letter-spacing: 6px;
}
.card-reader-list {
  width: 100%;
  border-collapse: collapse;
}
.card-reader-list th,
.card-reader-list td {
  padding: 8px;
  text-align: left;
  border-bottom: 1px solid #dfe7e3;
}
</style>
```

Add the route in `frontend/src/router/index.ts`, next to the other `/staff/settings/...` entries:

```ts
    {
      path: '/staff/settings/card-readers',
      name: 'staff-card-readers',
      component: () => import('@/features/idcard/CardReadersView.vue'),
      meta: { roles: ['manager'] },
    },
```

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/features/idcard src/router` and `npx vue-tsc --noEmit -p tsconfig.app.json`
Expected: PASS and no type errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/idcard frontend/src/router/index.ts
git commit -m "feat(idcard): manager page to add, re-pair and revoke card readers"
```

---

## Part D — End-to-end and device checks

### Task 16: API-level end-to-end test with a fake agent

**Files:**
- Create: `backend/cypress/e2e-v2/id-card-reader.cy.js` (backend branch)

Follows the existing `cy.api` style of `backend/cypress/e2e-v2/patient-identity-lookup.cy.js`; the spec plays the agent with plain requests.

- [ ] **Step 1: Write the spec**

```js
const validCard = {
  nationalId: '1101700005119', titleTh: 'นาย', firstNameTh: 'สมชาย', middleNameTh: null, lastNameTh: 'ใจดี',
  fullNameEn: 'Somchai Jaidee', birthDate: '1997-01-31', gender: 'M', address: '88/8 หมู่ที่ 3',
  issueDate: '2020-02-02', expiryDate: null, lifetime: true,
}

describe('ID card reader relay (agent simulated)', () => {
  let agent

  beforeEach(() => {
    cy.api('POST', '/api/id-card/workstations', { name: `e2e ${Date.now()}`, branchCode: '01' }).then(({ body }) => {
      cy.api({ method: 'POST', url: '/api/id-card/agent/pair', body: { code: body.pairingCode }, headers: { Authorization: '' } })
        .then(({ body: paired }) => {
          agent = { token: paired.token, workstationId: body.id }
        })
    })
  })

  const asAgent = (opts) => cy.api({ ...opts, headers: { Authorization: `Bearer ${agent.token}` } })

  it('registers from a card: start, agent reads, the staff receives it once', () => {
    asAgent({ method: 'GET', url: '/api/id-card/agent/next?waitSeconds=0' }).its('status').should('equal', 204)
    cy.api('POST', '/api/id-card/reads', { workstationId: agent.workstationId, purpose: 'REGISTER' }).then(({ status, body }) => {
      expect(status).to.equal(202)
      asAgent({ method: 'GET', url: '/api/id-card/agent/next?waitSeconds=3' }).then(({ body: command }) => {
        expect(command.requestId).to.equal(body.id)
        asAgent({ method: 'POST', url: '/api/id-card/agent/result', body: { requestId: body.id, status: 'OK', card: validCard } })
          .its('status').should('equal', 204)
      })
      cy.api('GET', `/api/id-card/reads/${body.id}`).its('body.card.nationalId').should('equal', validCard.nationalId)
      cy.api({ method: 'GET', url: `/api/id-card/reads/${body.id}`, failOnStatusCode: false }).its('status').should('equal', 404)
    })
  })

  it('refuses a revoked reader', () => {
    cy.api('POST', `/api/id-card/workstations/${agent.workstationId}/revoke`).its('status').should('equal', 204)
    asAgent({ method: 'GET', url: '/api/id-card/agent/next?waitSeconds=0', failOnStatusCode: false }).its('status').should('equal', 401)
  })
})
```

- [ ] **Step 2: Run it against a running backend**

Run (backend up on its usual E2E port; follow the project's existing Cypress runner for `e2e-v2`): `npx cypress run --spec backend/cypress/e2e-v2/id-card-reader.cy.js`
Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/cypress/e2e-v2/id-card-reader.cy.js
git commit -m "test(idcard): end-to-end relay with a simulated agent"
```

---

### Task 17: Device check with the real reader and cards

**Files:**
- Create: `docs/testing/2026-11-id-card-reader-device-check.md`

This is the gate that confirms the chip commands in Task 7 and the platform claims. It needs the real ZOWEETEK ZW-12026-3 and a real Thai ID card whose holder agrees to the test. **Do not paste the card's data into the results; record only masked values and pass/fail.**

- [ ] **Step 1: Write the checklist**

```markdown
# ตรวจเครื่องอ่านบัตรประชาชนกับเครื่องจริง

วันที่ทดสอบ: ____  ผู้ทดสอบ: ____  เครื่องอ่าน: ZOWEETEK ZW-12026-3  บัตรรุ่น/ปีที่ออก: ____ (ไม่ต้องจดข้อมูลส่วนตัว)

## A. ตัวกลางกับเครื่องอ่าน (ในแต่ละระบบปฏิบัติการ)

| ข้อ | ตรวจ | Windows | macOS | Linux |
|---|---|---|---|---|
| A1 | ระบบเห็นเครื่องอ่านโดยไม่ต้องลงไดรเวอร์เพิ่ม | | | |
| A2 | `ClinicIdCardAgent read-once` เสียบบัตร: `status=OK` | | | |
| A3 | เลขบัตรแสดงแบบปิดบัง และชื่อไทยถูกต้อง (ไม่เป็นตัวเพี้ยน) | | | |
| A4 | วันเกิด ปี ค.ศ. = ปีบนบัตร − 543 | | | |
| A5 | เพศตรงกับบัตร | | | |
| A6 | บัตรตลอดชีพ: `lifetime=true`; บัตรมีวันหมดอายุ: วันที่ถูกต้อง | | | |
| A7 | ที่อยู่ครบทุกส่วนและ `addressLength` สมเหตุสมผล | | | |
| A8 | ไม่เสียบบัตร: `status=NO_CARD` หลัง 30 วินาที | | | |
| A9 | ถอดเครื่องอ่าน: `status=NO_READER` | | | |
| A10 | ลองบัตรอย่างน้อย 2 รุ่น/ปีที่ออกต่างกัน (T=0 และ T=1 ถ้ามี) | | | |

ถ้า A2-A7 ล้ม ให้ตรวจชุดคำสั่งใน `ThaiIdCardReader.java` (Task 7) ก่อน แล้วแก้พร้อมเพิ่มเทสต์

## B. ทั้งระบบ (เว็บ + backend + ตัวกลาง)

| ข้อ | ตรวจ | ผล |
|---|---|---|
| B1 | จับคู่เครื่องด้วยรหัสจากหน้า "เครื่องอ่านบัตร" แล้วสถานะขึ้น "ออนไลน์" | |
| B2 | หน้าลงทะเบียน: กด "อ่านบัตรประชาชน" แล้วฟอร์มเติมครบ แก้ไขได้ ต้องติ๊กยินยอมก่อนบันทึก | |
| B3 | หน้าค้นหา: ผู้ป่วยที่มีอยู่เปิดโปรไฟล์; ผู้ป่วยที่ไม่มีขึ้นข้อความให้ลงทะเบียนใหม่ | |
| B4 | ปิดตัวกลางแล้วกดอ่าน: ขึ้นข้อความว่าออฟไลน์ และยังพิมพ์เองได้ | |
| B5 | เพิกถอนเครื่องแล้วตัวกลางหยุดทำงานและแจ้งให้จับคู่ใหม่ | |
| B6 | ตรวจตาราง `id_card_read_audit` มีแถวของแต่ละครั้ง และไม่มีข้อมูลบัตรในฐานข้อมูลก่อนกดบันทึกลงทะเบียน | |

## C. เบราว์เซอร์ (ผลเหมือนกันทุกตัวเพราะผ่านเซิร์ฟเวอร์) — ทำ B2 และ B3

| ระบบ | Chrome | Edge | Firefox | Safari |
|---|---|---|---|---|
| Windows | | | n/a | n/a |
| macOS | | n/a | | |
| Linux | | n/a | | n/a |

## ผลสรุป
รองรับอย่างเป็นทางการ: ____ (เฉพาะชุดที่ผ่านทั้ง A และ B และ C)
ปัญหาที่พบ: ____
```

- [ ] **Step 2: Run the checklist on the Windows counter PC, record results, fix and add tests for any failure in A, then repeat on macOS and Linux when those machines are available**

- [ ] **Step 3: Commit**

```bash
git add docs/testing/2026-11-id-card-reader-device-check.md
git commit -m "docs(idcard): device check for the card reader on each system"
```

---

## Self-Review

**Spec coverage** (section → task):
- 1 Goals/scope, register + lookup → Tasks 5, 14; no photo → Tasks 6–7 (no photo command), Global Constraints
- 2 Relay architecture / long-poll + polling → Tasks 4, 5, 9, 13
- 3.1 Agent → Tasks 6–11; 3.2 Backend → Tasks 1–5; 3.3 Web → Tasks 12–15
- 4.1 Pairing → Task 3 (+ Task 9 `pair`); 4.2 Register flow → Tasks 5, 13, 14; 4.3 Lookup server-side → Task 5 (`finishCard`, test `lookupFindsAnExistingPatientAndNeverReturnsTheCard`)
- 5 Field mapping → Tasks 6 (parse), 12 (form)
- 6 Tables/APIs → Tasks 2, 3, 5; 7 Security (hash, one-time result, branch scope, rate limit, no card data in audit) → Tasks 3–5; consent before save → existing `privacyConsent` (no change; covered in Task 17 B2)
- 8 Errors → Task 5 outcomes, Task 13 messages, Task 14 UI
- 9 Installers/autostart → Tasks 10, 11; 10 Tests → each task + Tasks 16, 17; 11 Risks → Task 17 and Task 7 note
- Spec scope narrowing applied: checksum only on card reads (Task 5); long-poll/polling (Tasks 4, 5, 9, 13).

**Known gaps and decisions to confirm before executing:** the migration number `V1023` must be re-checked against the base branch at execution time; the manager page is reachable by the link in the reader button and by URL (no entry in the back-office navigation yet, which touches `BackOfficeView.vue` and `CalendarSettingsView.vue` menus); signing/notarization of installers is not included; auto-update is out of scope.

**Placeholder scan:** none of "TBD/TODO/similar to Task N"; every code step has code; commands have expected outcomes.

**Type consistency:** `CardData` fields identical in backend (Task 4), agent (Task 6) and frontend `IdCardData` (Task 12); `ReadRequestStore` method names (`create/next/find/complete/fail/claim`) match their uses in `IdCardService` (Task 5); `WorkstationService` methods (`create/newPairingCode/revoke/pair/authenticate/touch/isOnline/require`) match uses in Tasks 3 and 5; REST paths and JSON field names match between backend tests (Tasks 3, 5), agent `BackendClient` (Task 9) and frontend `idCardApi` (Task 13); `ReadOutcome.Status` names (`OK/NO_CARD/NO_READER/ERROR`) match the backend's accepted `status` strings.
