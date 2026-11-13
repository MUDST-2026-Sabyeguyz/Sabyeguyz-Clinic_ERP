package com.sabyeguyz.clinicv2.idcard;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;

@Service
public class IdCardService {
    static final Duration AGENT_MAX_WAIT = Duration.ofSeconds(25);

    public record Started(
            String id,
            Instant expiresAt) {}

    public record AgentCommand(
            String requestId,
            String purpose) {}

    public record AgentResult(
            String requestId,
            String status,
            String message,
            CardData card) {}

    public record PatientBrief(
            Long id,
            String hn,
            String fullName) {}

    public record LookupResult(
            boolean exists,
            PatientBrief patient) {}

    /** What a finished read holds: who the number belongs to, and the card itself only when nobody does yet. */
    public record ReadResult(
            LookupResult lookup,
            CardData card) {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record ReadView(
            String status,
            String outcome,
            String message,
            CardData card,
            LookupResult lookup) {}

    private final ReadRequestStore store;
    private final ReadRateLimiter limiter;
    private final WorkstationService workstations;
    private final PatientRepository patients;
    private final IdCardReadAuditRepository audits;
    private final CurrentActor actor;
    private final BranchAccessPolicy branchAccess;

    public IdCardService(
            ReadRequestStore store,
            ReadRateLimiter limiter,
            WorkstationService workstations,
            PatientRepository patients,
            IdCardReadAuditRepository audits,
            CurrentActor actor,
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
    public Started start(
            Long workstationId,
            String purposeText) {
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
    public Optional<AgentCommand> next(
            CardReaderWorkstation workstation,
            int waitSeconds) {
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

    /** The agent saw a card in the reader and is about to read it: the screen can say "reading". */
    public void progress(
            CardReaderWorkstation workstation,
            String requestId,
            String stage) {
        ReadRequestStore.ReadRequest request = requestOf(workstation, requestId);

        if ("CARD_PRESENT".equals(stage)) store.markReading(request.id(), workstation.getId());
    }

    @Transactional
    public void agentResult(
            CardReaderWorkstation workstation,
            AgentResult result) {
        ReadRequestStore.ReadRequest request = requestOf(workstation, result.requestId());
        String status = result.status() == null ? "" : result.status();

        switch (status) {
            case "OK" -> finishCard(workstation, request, result.card());
            case "NO_CARD" -> failAndAudit(workstation, request, "NO_CARD", "ไม่พบบัตร ใส่บัตรแล้วลองใหม่");
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
            case PENDING -> new ReadView(
                    "PENDING",
                    null,
                    null,
                    null,
                    null);
            case READING -> new ReadView(
                    "READING",
                    null,
                    null,
                    null,
                    null);
            case FAILED -> {
                if ("TIMEOUT".equals(claim.outcome())) audit(claim.request(), "TIMEOUT");
                yield new ReadView(
                        "FAILED",
                        claim.outcome(),
                        claim.message(),
                        null,
                        null);
            }

            case DONE -> {
                ReadResult result = (ReadResult) claim.payload();
                yield new ReadView(
                        "DONE",
                        null,
                        null,
                        result.card(),
                        result.lookup());
            }
        };
    }

    private ReadRequestStore.ReadRequest requestOf(
            CardReaderWorkstation workstation,
            String requestId) {
        return store.find(requestId == null ? "" : requestId)
                .filter(found -> found.workstationId().equals(workstation.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบคำขออ่านบัตร"));
    }

    private void finishCard(
            CardReaderWorkstation workstation,
            ReadRequestStore.ReadRequest request,
            CardData card) {
        if (card == null || !ThaiNationalId.isValid(card.nationalId())) {
            failAndAudit(workstation, request, "INVALID_CARD", "เลขบัตรไม่ผ่านการตรวจหลักตรวจสอบ อ่านบัตรใหม่หรือกรอกเอง");
            return;
        }

        LookupResult lookup = patients.findByNationalId(card.nationalId())
                .map(patient -> new LookupResult(
                        true,
                        new PatientBrief(
                                patient.getId(),
                                patient.getPatientHn(),
                                fullName(patient))))
                .orElseGet(() -> new LookupResult(false, null));

        // a patient already in the system: the browser learns only who it is, never what the card says
        store.complete(
                request.id(),
                workstation.getId(),
                new ReadResult(lookup, lookup.exists() ? null : card));

        audit(request, "SUCCESS");
    }

    private void failAndAudit(
            CardReaderWorkstation workstation,
            ReadRequestStore.ReadRequest request,
            String outcome,
            String message) {
        store.fail(request.id(), workstation.getId(), outcome, message);

        audit(request, outcome);
    }

    private void audit(
            ReadRequestStore.ReadRequest request,
            String outcome) {
        audits.save(new IdCardReadAudit(
                request.workstationId(),
                request.userId(),
                request.branchId(),
                request.purpose().name(),
                outcome));
    }

    private static ReadRequestStore.Purpose parsePurpose(String value) {
        try {
            return ReadRequestStore.Purpose.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("วัตถุประสงค์ต้องเป็น REGISTER");
        }
    }

    private static String fullName(Patient patient) {
        return (patient.getPatientFname() + " " + patient.getPatientLname()).trim();
    }
}
