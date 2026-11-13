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
    public enum Purpose { REGISTER }

    public enum Status { PENDING, READING, DONE, FAILED }

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

    /** The card is in the reader: the screen can say it is being read. Does nothing once the read has finished. */
    public void markReading(String id, Long workstationId) {
        Entry entry = owned(id, workstationId);
        if (entry.status == Status.PENDING) entry.status = Status.READING;
    }

    /** The state of a read for the user who started it; a finished state is delivered once and removed. */
    public Optional<Claim> claim(String id, Long userId) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.request.userId().equals(userId)) return Optional.empty();
        if (entry.status == Status.PENDING || entry.status == Status.READING) {
            if (entry.request.expiresAt().isAfter(clock.instant())) {
                return Optional.of(new Claim(entry.status, entry.request, null, null, null));
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
        entries.values().removeIf(entry -> entry.request.expiresAt().plus(ttl).isBefore(now));
    }
}
