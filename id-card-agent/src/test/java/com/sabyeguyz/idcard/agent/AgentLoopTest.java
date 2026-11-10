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
    final BlockingQueue<JsonNode> progressCalls = new LinkedBlockingQueue<>();
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
        server.createContext("/api/id-card/agent/progress", exchange -> {
            progressCalls.add(json.readTree(exchange.getRequestBody().readAllBytes()));
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
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"), (wait, cardPresent) -> { throw new AssertionError("no read expected"); }, Duration.ofSeconds(1));
        assertFalse(loop.runOnce());
        assertEquals("Bearer tok-1", lastAuth.get());
        assertTrue(results.isEmpty());
    }

    @Test
    void readsTheCardAndSendsTheResultForACommand() throws Exception {
        nextResponse.set("{\"requestId\":\"r-1\",\"purpose\":\"REGISTER\"}");
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"), (wait, cardPresent) -> CardSource.ReadOutcome.ok(card()), Duration.ofSeconds(1));
        assertTrue(loop.runOnce());
        JsonNode sent = results.poll(2, TimeUnit.SECONDS);
        assertEquals("r-1", sent.path("requestId").asText());
        assertEquals("OK", sent.path("status").asText());
        assertEquals("1101700005119", sent.path("card").path("nationalId").asText());
        assertEquals("1997-01-31", sent.path("card").path("birthDate").asText());
    }

    @Test
    void reportsWhyTheCardCouldNotBeRead() throws Exception {
        nextResponse.set("{\"requestId\":\"r-2\",\"purpose\":\"REGISTER\"}");
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"),
                (wait, cardPresent) -> CardSource.ReadOutcome.of(CardSource.Status.NO_CARD, "ไม่พบบัตร"), Duration.ofSeconds(1));
        loop.runOnce();
        JsonNode sent = results.poll(2, TimeUnit.SECONDS);
        assertEquals("NO_CARD", sent.path("status").asText());
        assertTrue(sent.path("card").isMissingNode() || sent.path("card").isNull());
    }

    @Test
    void tellsTheBackendThatAReadStartedBeforeSendingTheResult() throws Exception {
        nextResponse.set("{\"requestId\":\"r-3\",\"purpose\":\"REGISTER\"}");
        var loop = new AgentLoop(new BackendClient(baseUrl, "tok-1"), (wait, cardPresent) -> {
            cardPresent.run();
            return CardSource.ReadOutcome.ok(card());
        }, Duration.ofSeconds(1));
        loop.runOnce();
        JsonNode progress = progressCalls.poll(2, TimeUnit.SECONDS);
        assertEquals("r-3", progress.path("requestId").asText());
        assertEquals("CARD_PRESENT", progress.path("stage").asText());
        assertNotNull(results.poll(2, TimeUnit.SECONDS));
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
