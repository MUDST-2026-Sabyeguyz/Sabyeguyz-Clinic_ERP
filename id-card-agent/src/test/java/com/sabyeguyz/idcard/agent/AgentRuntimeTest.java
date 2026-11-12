package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRuntimeTest {
    @TempDir Path dir;
    HttpServer backend;
    String backendUrl;
    final AtomicInteger polls = new AtomicInteger();
    final AtomicBoolean revokeTokens = new AtomicBoolean();
    final ObjectMapper json = new ObjectMapper();
    AgentRuntime runtime;

    @BeforeEach
    void startFakeBackend() throws Exception {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/api/id-card/agent/pair", exchange -> {
            JsonNode body = json.readTree(exchange.getRequestBody().readAllBytes());
            boolean good = "ABCD2345".equals(body.path("code").asText());
            byte[] bytes = (good ? "{\"token\":\"tok-1\",\"workstationName\":\"เคาน์เตอร์ 1\",\"branchCode\":\"01\",\"workstationId\":7}" : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(good ? 200 : 400, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        backend.createContext("/api/id-card/agent/next", exchange -> {
            polls.incrementAndGet();
            try {
                Thread.sleep(40);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(revokeTokens.get() ? 401 : 204, -1);
            exchange.close();
        });
        backend.start();
        backendUrl = "http://127.0.0.1:" + backend.getAddress().getPort();
        runtime = new AgentRuntime(dir.resolve("config.json"),
                config -> new AgentLoop(new BackendClient(config.backendUrl(), config.token()),
                        (wait, present) -> { throw new AssertionError("no read expected"); }, Duration.ofSeconds(1)),
                status -> {});
    }

    @AfterEach
    void stop() {
        runtime.stop();
        backend.stop(0);
    }

    static void eventually(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) Thread.sleep(30);
        assertTrue(condition.getAsBoolean(), "condition was not met in time");
    }

    @Test
    void startsUnpairedWhenThereIsNoSavedPairing() {
        runtime.start();
        assertFalse(runtime.status().paired());
        assertNull(runtime.status().workstationId());
        assertEquals(0, polls.get());
    }

    @Test
    void pairingSavesTheReaderAndStartsServingIt() throws Exception {
        runtime.start();
        LocalServer.Status status = runtime.pair(backendUrl, "ABCD2345");
        assertTrue(status.paired());
        assertEquals("เคาน์เตอร์ 1", status.workstationName());
        assertEquals("01", status.branchCode());
        assertEquals(7L, status.workstationId());
        AgentConfig saved = AgentConfig.load(dir.resolve("config.json"));
        assertEquals("tok-1", saved.token());
        assertEquals(7L, saved.workstationId());
        eventually(() -> polls.get() > 0);
    }

    @Test
    void aWrongCodeLeavesTheAgentAsItWas() throws Exception {
        runtime.start();
        assertThrows(java.io.IOException.class, () -> runtime.pair(backendUrl, "WRONG123"));
        assertFalse(runtime.status().paired());
        assertFalse(Files.exists(dir.resolve("config.json")));
    }

    @Test
    void resumesASavedPairingWhenItStarts() throws Exception {
        new AgentConfig(backendUrl, "tok-1", "เคาน์เตอร์ 1", "01", 7L).save(dir.resolve("config.json"));
        runtime.start();
        assertTrue(runtime.status().paired());
        eventually(() -> polls.get() > 0);
    }

    @Test
    void aPairingFileWrittenByAnOlderVersionStillWorks() throws Exception {
        Files.writeString(dir.resolve("config.json"), "{\"backendUrl\":\"" + backendUrl + "\",\"token\":\"old\"}");
        runtime.start();
        assertTrue(runtime.status().paired());
        assertNull(runtime.status().workstationId(), "the page then falls back to the reader the user picked");
    }

    @Test
    void anUnreadablePairingFileMeansUnpairedNotACrash() throws Exception {
        Files.writeString(dir.resolve("config.json"), "{ not json");
        runtime.start();
        assertFalse(runtime.status().paired());
    }

    @Test
    void reportsUnpairedOnceTheBackendRevokesThisReader() throws Exception {
        revokeTokens.set(true);
        new AgentConfig(backendUrl, "tok-1", "เคาน์เตอร์ 1", "01", 7L).save(dir.resolve("config.json"));
        runtime.start();
        eventually(() -> !runtime.status().paired());
    }

    @Test
    void pairingAgainAfterRevocationWorks() throws Exception {
        revokeTokens.set(true);
        new AgentConfig(backendUrl, "tok-1", "เคาน์เตอร์ 1", "01", 7L).save(dir.resolve("config.json"));
        runtime.start();
        eventually(() -> !runtime.status().paired());
        revokeTokens.set(false);
        assertTrue(runtime.pair(backendUrl, "ABCD2345").paired());
        int before = polls.get();
        eventually(() -> polls.get() > before);
    }

    @Test
    void allowsTheConfiguredSitesAndTheBackendItIsPairedWith() throws Exception {
        Set<String> configured = Set.of("https://erp.clinic.example");
        assertEquals(configured, runtime.allowedOrigins(configured));
        runtime.start();
        runtime.pair(backendUrl, "ABCD2345");
        assertTrue(runtime.allowedOrigins(configured).contains(backendUrl));
        assertTrue(runtime.allowedOrigins(configured).contains("https://erp.clinic.example"));
    }

    @Test
    void knowsWhereTheSettingsPageIs() throws Exception {
        assertTrue(runtime.settingsUrl(Set.of()).isEmpty());
        assertEquals("https://erp.clinic.example/staff/settings/card-readers",
                runtime.settingsUrl(Set.of("https://erp.clinic.example")).orElseThrow());
        runtime.start();
        runtime.pair(backendUrl, "ABCD2345");
        assertEquals(backendUrl + "/staff/settings/card-readers", runtime.settingsUrl(Set.of()).orElseThrow());
    }
}
