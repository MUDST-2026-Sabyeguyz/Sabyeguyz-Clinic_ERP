package com.sabyeguyz.idcard.agent;

import java.time.Duration;

/** Waits for a card at the reader and reads it; tells the caller the moment a card is detected. */
interface CardSource {
    enum Status { OK, NO_CARD, NO_READER, ERROR }

    record ReadOutcome(Status status, CardData card, String message) {
        static ReadOutcome ok(CardData card) { return new ReadOutcome(Status.OK, card, null); }
        static ReadOutcome of(Status status, String message) { return new ReadOutcome(status, null, message); }
    }

    ReadOutcome readWhenPresent(Duration wait, Runnable onCardPresent);
}
