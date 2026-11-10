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
        String requestId = command.get().requestId();
        client.sendResult(requestId, source.readWhenPresent(cardWait, () -> client.sendProgress(requestId)));
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
