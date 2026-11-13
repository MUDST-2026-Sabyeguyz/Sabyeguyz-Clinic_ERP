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
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
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
    void theUserSeesReadingOnceTheCardIsInTheReader() {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        store.markReading(request.id(), 7L);
        assertEquals(ReadRequestStore.Status.READING, store.claim(request.id(), 1L).orElseThrow().status());
        assertEquals(ReadRequestStore.Status.READING, store.claim(request.id(), 1L).orElseThrow().status(), "reading is not consumed");
        store.complete(request.id(), 7L, "card");
        assertEquals(ReadRequestStore.Status.DONE, store.claim(request.id(), 1L).orElseThrow().status());
    }

    @Test
    void aResultFromAnotherWorkstationIsRejected() {
        var request = store.create(7L, 1L, 10L, ReadRequestStore.Purpose.REGISTER);
        assertThrows(NoSuchElementException.class, () -> store.complete(request.id(), 8L, "card"));
        assertThrows(NoSuchElementException.class, () -> store.fail(request.id(), 8L, "ERROR", "x"));
    }
}
