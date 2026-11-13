package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class IsolatedCardSource implements CardSource {
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private final ProcessStarter starter;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    IsolatedCardSource() {
        this(command -> new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start());
    }

    IsolatedCardSource(ProcessStarter starter) {
        this.starter = starter;
    }

    @Override
    public boolean readerPresent() {
        try {
            return "CONNECTED".equals(exchange(List.of("presence"), Duration.ofSeconds(5), () -> {}));
        } catch (IOException unavailable) {
            return false;
        }
    }

    @Override
    public ReadOutcome readWhenPresent(Duration wait, Runnable onCardPresent) {
        try {
            String result = exchange(List.of("read", Long.toString(wait.toMillis())),
                    wait.plusSeconds(10), onCardPresent);
            if (!result.startsWith("RESULT ")) return failed();
            return json.readValue(result.substring(7), ReadOutcome.class);
        } catch (IOException unavailable) {
            return failed();
        }
    }

    private ReadOutcome failed() {
        return ReadOutcome.of(Status.ERROR, "ติดต่อเครื่องอ่านบัตรไม่ได้ กรุณาลองอีกครั้ง");
    }

    private String exchange(List<String> arguments, Duration timeout, Runnable onCardPresent) throws IOException {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("java.class.path"), CardReaderWorker.class.getName()));
        command.addAll(arguments);
        Process process = starter.start(List.copyOf(command));
        var executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "card-reader-response");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var response = executor.submit(() -> {
                try (var input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    boolean reported = false;
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (line.length() > 65536) throw new IOException("Invalid reader response");
                        if ("READING".equals(line)) {
                            if (!reported) onCardPresent.run();
                            reported = true;
                        } else {
                            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) || process.exitValue() != 0) {
                                throw new IOException("Reader process failed");
                            }
                            return line;
                        }
                    }
                    throw new IOException("Reader process closed");
                }
            });
            return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Reader cancelled");
        } catch (ExecutionException | TimeoutException unavailable) {
            throw new IOException("Reader unavailable");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            executor.shutdownNow();
            process.getOutputStream().close();
            process.getErrorStream().close();
        }
    }
}
