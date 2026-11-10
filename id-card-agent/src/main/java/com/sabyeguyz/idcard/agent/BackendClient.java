package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The agent's calls to the backend. */
final class BackendClient {
    static final class UnauthorizedException extends IOException {
        UnauthorizedException() {
            super("โทเคนของเครื่องอ่านบัตรถูกเพิกถอนหรือไม่ถูกต้อง");
        }
    }

    record Command(String requestId, String purpose) {}

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String backendUrl;
    private final String token;

    BackendClient(String backendUrl, String token) {
        this.backendUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
        this.token = token;
    }

    static AgentConfig pair(String backendUrl, String code) throws IOException {
        String base = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(base + "/api/id-card/agent/pair"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("code", code.trim().toUpperCase()))))
                .build());
        if (response.statusCode() != 200) throw new IOException("จับคู่ไม่สำเร็จ: รหัสไม่ถูกต้องหรือหมดอายุ");
        return new AgentConfig(base, JSON.readTree(response.body()).path("token").asText());
    }

    /** Long-poll: returns after a command arrives or {@code waitSeconds} pass. */
    Optional<Command> next(int waitSeconds) throws IOException {
        HttpResponse<String> response = send(authorized("/api/id-card/agent/next?waitSeconds=" + waitSeconds,
                Duration.ofSeconds(waitSeconds + 15)).GET().build());
        if (response.statusCode() == 401) throw new UnauthorizedException();
        if (response.statusCode() == 204) return Optional.empty();
        if (response.statusCode() != 200) throw new IOException("backend ตอบ " + response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        return Optional.of(new Command(body.path("requestId").asText(), body.path("purpose").asText()));
    }

    void sendResult(String requestId, CardSource.ReadOutcome outcome) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("status", outcome.status().name());
        body.put("message", outcome.message());
        body.put("card", outcome.card());
        HttpResponse<String> response = send(authorized("/api/id-card/agent/result", Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build());
        if (response.statusCode() == 401) throw new UnauthorizedException();
        if (response.statusCode() >= 300) throw new IOException("ส่งผลไม่สำเร็จ: " + response.statusCode());
    }

    /** Best effort: tells the backend a card is in the reader so the screen can say it is reading. */
    void sendProgress(String requestId) {
        try {
            send(authorized("/api/id-card/agent/progress", Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JSON.writeValueAsString(Map.of("requestId", requestId, "stage", "CARD_PRESENT"))))
                    .build());
        } catch (IOException ignored) {
            // the result still arrives; only the "reading" message on the screen is lost
        }
    }

    private HttpRequest.Builder authorized(String path, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(backendUrl + path)).timeout(timeout).header("X-Agent-Token", token);
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("ถูกยกเลิก", interrupted);
        }
    }
}
