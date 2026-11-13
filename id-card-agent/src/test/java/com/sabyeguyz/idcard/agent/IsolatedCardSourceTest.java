package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IsolatedCardSourceTest {
    @Test
    void opensAFreshProcessForUnplugAndReplug() {
        Queue<String> replies = new ArrayDeque<>(List.of("CONNECTED\n", "DISCONNECTED\n", "CONNECTED\n"));
        AtomicInteger launches = new AtomicInteger();
        var source = new IsolatedCardSource(command -> {
            assertEquals(CardReaderWorker.class.getName(), command.get(3));
            assertEquals("presence", command.get(4));
            launches.incrementAndGet();
            return new FinishedProcess(replies.remove(), 0);
        });

        assertTrue(source.readerPresent());
        assertFalse(source.readerPresent());
        assertTrue(source.readerPresent());
        assertEquals(3, launches.get());
    }

    @Test
    void forwardsProgressOnceAndReturnsTheReadResult() {
        var source = new IsolatedCardSource(command -> {
            assertEquals("read", command.get(4));
            assertEquals("1000", command.get(5));
            return new FinishedProcess("READING\nREADING\nRESULT {\"status\":\"NO_CARD\",\"card\":null,\"message\":null}\n", 0);
        });
        AtomicInteger progress = new AtomicInteger();
        var result = source.readWhenPresent(Duration.ofSeconds(1), progress::incrementAndGet);
        assertEquals(CardSource.Status.NO_CARD, result.status());
        assertEquals(1, progress.get());
    }

    @Test
    void failsClosedIfTheProcessCannotStart() {
        var source = new IsolatedCardSource(command -> { throw new IOException("unavailable"); });
        assertFalse(source.readerPresent());
        assertEquals(CardSource.Status.ERROR, source.readWhenPresent(Duration.ofSeconds(1), () -> {}).status());
    }

    @Test
    void rejectsAnUnsuccessfulProcessEvenIfItPrintedConnected() {
        var source = new IsolatedCardSource(command -> new FinishedProcess("CONNECTED\n", 1));
        assertFalse(source.readerPresent());
    }

    @Test
    void rejectsMalformedOrEmptyOutput() {
        var malformed = new IsolatedCardSource(command -> new FinishedProcess("RESULT invalid\n", 0));
        assertEquals(CardSource.Status.ERROR, malformed.readWhenPresent(Duration.ofSeconds(1), () -> {}).status());
        var empty = new IsolatedCardSource(command -> new FinishedProcess("", 0));
        assertFalse(empty.readerPresent());
    }

    private static final class FinishedProcess extends Process {
        private final InputStream input;
        private final int exitCode;
        private final OutputStream output = new ByteArrayOutputStream();

        FinishedProcess(String response, int exitCode) {
            input = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override public OutputStream getOutputStream() { return output; }
        @Override public InputStream getInputStream() { return input; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exitCode; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
        @Override public int exitValue() { return exitCode; }
        @Override public boolean isAlive() { return false; }
        @Override public void destroy() {}
    }
}
