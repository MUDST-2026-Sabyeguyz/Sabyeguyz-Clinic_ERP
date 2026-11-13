package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalServerTest {
    static final String ALLOWED = "https://erp.clinic.example";

    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    final List<String> pairCalls = new ArrayList<>();
    LocalServer.Status current = new LocalServer.Status(false, null, null, null, "1.0.0");
    LocalServer server;
    String base;

    @BeforeEach
    void start() throws Exception {
        server = new LocalServer(0, () -> Set.of(ALLOWED), new LocalServer.Controller() {
            @Override
            public LocalServer.Status status() {
                return current;
            }

            @Override
            public LocalServer.Status pair(String backendUrl, String code) throws IOException {
                pairCalls.add(backendUrl + " " + code);
                if (!code.equals("ABCD2345")) throw new IOException("จับคู่ไม่สำเร็จ: รหัสไม่ถูกต้องหรือหมดอายุ");
                current = new LocalServer.Status(true, "เคาน์เตอร์ 1", "01", 7L, "1.0.0");
                return current;
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    HttpResponse<String> send(String method, String path, String origin, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) request.header("Origin", origin);
        if (body != null) request.header("Content-Type", "application/json");
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void refusesARequestWithNoOrigin() throws Exception {
        assertEquals(403, send("GET", "/status", null, null).statusCode());
    }

    @Test
    void refusesASiteTheClinicDidNotAllow() throws Exception {
        HttpResponse<String> response = send("GET", "/status", "https://evil.example", null);
        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty(), "no CORS grant for a stranger");
        assertEquals(403, send("POST", "/pair", "https://evil.example", "{\"code\":\"ABCD2345\"}").statusCode());
        assertTrue(pairCalls.isEmpty());
    }

    @Test
    void refusesARequestAddressedToAnotherNameForThisMachine() throws Exception {
        // DNS rebinding: an attacker's name resolves to 127.0.0.1, so the request carries that name in Host
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.getOutputStream().write(("GET /status HTTP/1.1\r\nHost: evil.example:" + server.port() + "\r\nOrigin: " + ALLOWED
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(reply.startsWith("HTTP/1.1 403"), reply);
        }
    }

    @Test
    void tellsAnAllowedSiteWhetherTheAgentIsPaired() throws Exception {
        HttpResponse<String> unpaired = send("GET", "/status", ALLOWED, null);
        assertEquals(200, unpaired.statusCode());
        assertEquals(ALLOWED, unpaired.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertFalse(json.readTree(unpaired.body()).path("paired").asBoolean());

        current = new LocalServer.Status(true, "เคาน์เตอร์ 1", "01", 7L, "1.0.0");
        JsonNode paired = json.readTree(send("GET", "/status", ALLOWED, null).body());
        assertTrue(paired.path("paired").asBoolean());
        assertEquals(7, paired.path("workstationId").asLong());
        assertEquals("01", paired.path("branchCode").asText());
        assertFalse(paired.has("token"), "the token never leaves the agent");
    }

    @Test
    void answersChromesPrivateNetworkPreflight() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/pair", ALLOWED, null);
        assertEquals(204, response.statusCode());
        assertEquals(ALLOWED, response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Private-Network").orElse(""));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
        assertEquals(403, send("OPTIONS", "/pair", "https://evil.example", null).statusCode());
    }

    @Test
    void pairsWithTheSiteThatAskedUsingTheCode() throws Exception {
        HttpResponse<String> response = send("POST", "/pair", ALLOWED, "{\"code\":\"ABCD2345\"}");
        assertEquals(200, response.statusCode());
        assertEquals(List.of(ALLOWED + " ABCD2345"), pairCalls);
        assertTrue(json.readTree(response.body()).path("paired").asBoolean());
    }

    @Test
    void passesOnWhyPairingFailed() throws Exception {
        HttpResponse<String> response = send("POST", "/pair", ALLOWED, "{\"code\":\"WRONG123\"}");
        assertEquals(400, response.statusCode());
        assertTrue(json.readTree(response.body()).path("error").asText().contains("รหัสไม่ถูกต้อง"));
    }

    @Test
    void refusesACodeThatIsNotShapedLikeOne() throws Exception {
        assertEquals(400, send("POST", "/pair", ALLOWED, "{\"code\":\"x\"}").statusCode());
        assertEquals(400, send("POST", "/pair", ALLOWED, "{\"code\":\"AB CD/..\"}").statusCode());
        assertEquals(400, send("POST", "/pair", ALLOWED, "not json").statusCode());
        assertEquals(400, send("POST", "/pair", ALLOWED, "{}").statusCode());
        assertTrue(pairCalls.isEmpty());
    }

    @Test
    void refusesAnOversizedBody() throws Exception {
        String padding = "x".repeat(10_000);
        assertEquals(400, send("POST", "/pair", ALLOWED, "{\"code\":\"ABCD2345\",\"pad\":\"" + padding + "\"}").statusCode());
        assertTrue(pairCalls.isEmpty());
    }

    @Test
    void willNotRedeemACodeAtASiteTheClinicDidNotAllow() throws Exception {
        // a page cannot make the agent hand its pairing code to a server of the page's choosing
        HttpResponse<String> response = send("POST", "/pair", ALLOWED, "{\"code\":\"ABCD2345\",\"backendUrl\":\"https://evil.example\"}");
        assertEquals(403, response.statusCode());
        assertTrue(pairCalls.isEmpty());
    }

    @Test
    void offersNothingElse() throws Exception {
        assertEquals(404, send("GET", "/read", ALLOWED, null).statusCode());
        assertEquals(404, send("GET", "/", ALLOWED, null).statusCode());
        assertEquals(405, send("POST", "/status", ALLOWED, "{}").statusCode());
        assertEquals(405, send("GET", "/pair", ALLOWED, null).statusCode());
    }

    @Test
    void normalizesOriginsSoSpellingDoesNotMatter() {
        assertEquals("https://erp.clinic.example", LocalServer.normalizeOrigin("HTTPS://ERP.Clinic.example/"));
        assertEquals("http://localhost:5173", LocalServer.normalizeOrigin("http://localhost:5173/staff?x=1"));
        assertNull(LocalServer.normalizeOrigin("ftp://erp.clinic.example"));
        assertNull(LocalServer.normalizeOrigin("not a url"));
        assertNull(LocalServer.normalizeOrigin(null));
        assertEquals(Set.of("https://a.example", "http://b.example:8080"),
                LocalServer.parseOrigins("https://a.example, junk", null, "http://b.example:8080"));
    }
}
