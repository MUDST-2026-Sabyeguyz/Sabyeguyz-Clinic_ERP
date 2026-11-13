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

    public record Paired(String token, String workstationName, String branchCode, Long workstationId) {}

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
        return new Paired(token, workstation.getName(), workstation.getBranch().getBranchCode(), workstation.getId());
    }

    /**
     * The workstation behind the {@code X-Agent-Token} header, or 401. The agent does not use {@code Authorization: Bearer}
     * because the JWT resource server would try to decode it as a user token and refuse it before it reaches us.
     */
    @Transactional(readOnly = true)
    public CardReaderWorkstation authenticate(String agentToken) {
        String token = agentToken == null ? "" : agentToken.trim();
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
