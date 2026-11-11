package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the clinic's web page may ask of this agent, over {@code http://127.0.0.1}: say whether it is installed and paired,
 * and accept a pairing code. Reading a card is never offered here; that always goes through the backend.
 *
 * <p>Any website can send requests to 127.0.0.1, so every request must come from an origin the clinic allowed
 * (the browser always sends {@code Origin} on such calls) and must be addressed to this machine by its own name
 * (which stops DNS rebinding). Anything else is refused before it is looked at.
 */
final class LocalServer {
    static final int DEFAULT_PORT = 47631;
    private static final int MAX_BODY_BYTES = 4096;
    private static final ObjectMapper JSON = new ObjectMapper();

    record Status(boolean paired, String workstationName, String branchCode, Long workstationId, String version) {}

    interface Controller {
        Status status();

        /** Pairs with the backend at {@code backendUrl} using the one-time code; throws with a Thai message when it fails. */
        Status pair(String backendUrl, String code) throws IOException;
    }

    private final HttpServer server;
    private final Supplier<Set<String>> allowedOrigins;
    private final Controller controller;
    private final int port;

    LocalServer(int port, Supplier<Set<String>> allowedOrigins, Controller controller) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port), 0);
        this.port = server.getAddress().getPort();
        this.allowedOrigins = allowedOrigins;
        this.controller = controller;
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "local-server");
            thread.setDaemon(true);
            return thread;
        }));
    }

    int port() {
        return port;
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    /** {@code scheme://host[:port]} in lower case with no path, or null when the text is not such an origin. */
    static String normalizeOrigin(String text) {
        if (text == null) return null;
        try {
            URI uri = URI.create(text.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) return null;
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Comma- or space-separated origins from any number of settings; entries that are not origins are dropped. */
    static Set<String> parseOrigins(String... lists) {
        return Arrays.stream(lists)
                .filter(list -> list != null && !list.isBlank())
                .flatMap(list -> Arrays.stream(list.split("[,\\s]+")))
                .map(LocalServer::normalizeOrigin)
                .filter(origin -> origin != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!hostIsThisMachine(exchange.getRequestHeaders().getFirst("Host"))) {
                reply(exchange, 403, Map.of("error", "ปฏิเสธ"));
                return;
            }
            String origin = normalizeOrigin(exchange.getRequestHeaders().getFirst("Origin"));
            if (origin == null || !allowedOrigins.get().contains(origin)) {
                reply(exchange, 403, Map.of("error", "ปฏิเสธ"));
                return;
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            String method = exchange.getRequestMethod();
            if (method.equals("OPTIONS")) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                // Chrome asks permission before a public site may reach a private address
                exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/status")) {
                if (!method.equals("GET")) reply(exchange, 405, Map.of("error", "ไม่รองรับวิธีนี้"));
                else reply(exchange, 200, controller.status());
            } else if (path.equals("/pair")) {
                if (!method.equals("POST")) reply(exchange, 405, Map.of("error", "ไม่รองรับวิธีนี้"));
                else pair(exchange, origin);
            } else {
                reply(exchange, 404, Map.of("error", "ไม่พบ"));
            }
        }
    }

    private void pair(HttpExchange exchange, String origin) throws IOException {
        JsonNode body;
        try {
            body = JSON.readTree(readBounded(exchange.getRequestBody()));
        } catch (IOException invalid) {
            reply(exchange, 400, Map.of("error", "ข้อมูลที่ส่งมาไม่ถูกต้อง"));
            return;
        }
        String code = body == null ? "" : body.path("code").asText("").trim();
        if (!code.matches("[A-Za-z0-9]{4,16}")) {
            reply(exchange, 400, Map.of("error", "รหัสจับคู่ไม่ถูกต้อง"));
            return;
        }
        // the code is redeemed at the site that asked, unless the page names another site the clinic also allowed
        String backend = origin;
        if (body.hasNonNull("backendUrl")) {
            String requested = normalizeOrigin(body.get("backendUrl").asText());
            if (requested == null || !allowedOrigins.get().contains(requested)) {
                reply(exchange, 403, Map.of("error", "ปฏิเสธ"));
                return;
            }
            backend = requested;
        }
        try {
            reply(exchange, 200, controller.pair(backend, code));
        } catch (IOException failed) {
            reply(exchange, 400, Map.of("error", failed.getMessage() == null ? "จับคู่ไม่สำเร็จ" : failed.getMessage()));
        }
    }

    private boolean hostIsThisMachine(String host) {
        if (host == null) return false;
        String value = host.trim().toLowerCase(Locale.ROOT);
        return value.equals("127.0.0.1:" + port) || value.equals("localhost:" + port);
    }

    private static byte[] readBounded(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("ข้อมูลใหญ่เกินไป");
        return bytes;
    }

    private static void reply(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
