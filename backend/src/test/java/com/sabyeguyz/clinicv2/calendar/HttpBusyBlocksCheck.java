package com.sabyeguyz.clinicv2.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the real HttpGoogleCalendarClient against a small local stand-in for Google (no account, no network) and checks
 * which events count as Busy. Prints BUSY_CHECK_OK when everything is as expected, otherwise throws.
 *
 *   java -cp "target/classes:target/test-classes:$(cat target/test-classpath.txt)" com.sabyeguyz.clinicv2.calendar.HttpBusyBlocksCheck
 */
public class HttpBusyBlocksCheck {
    private static final String EVENTS = """
            {"items": [
              {"id":"a","status":"cancelled","start":{"dateTime":"2026-10-17T10:00:00+07:00"},"end":{"dateTime":"2026-10-17T11:00:00+07:00"}},
              {"id":"b","status":"confirmed","transparency":"transparent","start":{"dateTime":"2026-10-17T12:00:00+07:00"},"end":{"dateTime":"2026-10-17T13:00:00+07:00"}},
              {"id":"c","status":"confirmed","description":"Remote edited note","start":{"dateTime":"2026-10-17T14:00:00+07:00"},"end":{"dateTime":"2026-10-17T15:00:00+07:00"},
               "extendedProperties":{"private":{"appointmentId":"42","source":"clinic-erp"}}},
              {"id":"d","status":"confirmed","extendedProperties":{"private":{"busyScope":"branch"}},"start":{"dateTime":"2026-10-17T09:30:00+07:00"},"end":{"dateTime":"2026-10-17T10:30:00+07:00"}},
              {"id":"e","status":"confirmed","extendedProperties":{"private":{"busyScope":"therapist","staffId":"42"}},"start":{"dateTime":"2026-10-17T03:00:00Z"},"end":{"dateTime":"2026-10-17T04:00:00Z"}},
              {"id":"f","status":"confirmed","extendedProperties":{"private":{"busyScope":"branch"}},"start":{"date":"2026-10-18"},"end":{"date":"2026-10-20"}},
              {"id":"g","summary":"Holiday for everyone","status":"confirmed","start":{"dateTime":"2026-10-17T16:00:00+07:00"},"end":{"dateTime":"2026-10-17T17:00:00+07:00"}}
            ]}
            """;

    public static void main(String[] args) throws Exception {
        AtomicReference<String> eventsQuery = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = "{\"access_token\":\"test-token\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/calendar/v3/calendars/", exchange -> {
            eventsQuery.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = EVENTS.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            CalendarProperties properties = new CalendarProperties();
            properties.setEnabled(true);
            properties.setClientId("id");
            properties.setClientSecret("secret");
            properties.setRefreshToken("refresh");
            properties.setTokenUrl(base + "/token");
            properties.setApiBaseUrl(base + "/calendar/v3");
            HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(properties, new ObjectMapper());

            List<GoogleCalendarClient.BusyBlock> blocks = client.busyBlocks("branch@group.calendar.google.com",
                    LocalDateTime.of(2026, 10, 17, 0, 0), LocalDateTime.of(2026, 10, 18, 0, 0));

            check(blocks.size() == 3, "expected 3 busy blocks (not the cancelled, free or own event) but got " + blocks);
            check(blocks.contains(new GoogleCalendarClient.BusyBlock(LocalDateTime.of(2026, 10, 17, 9, 30), LocalDateTime.of(2026, 10, 17, 10, 30))),
                    "the plain event is missing: " + blocks);
            check(blocks.contains(new GoogleCalendarClient.BusyBlock(LocalDateTime.of(2026, 10, 17, 10, 0), LocalDateTime.of(2026, 10, 17, 11, 0), 42L)),
                    "a UTC event must be read in clinic time (03:00Z = 10:00 Bangkok): " + blocks);
            check(blocks.contains(new GoogleCalendarClient.BusyBlock(LocalDateTime.of(2026, 10, 18, 0, 0), LocalDateTime.of(2026, 10, 20, 0, 0))),
                    "an all-day event covers whole days: " + blocks);
            check(eventsQuery.get().contains("singleEvents=true") && eventsQuery.get().contains("timeMin=2026-10-17T00%3A00%3A00%2B07%3A00")
                    && eventsQuery.get().contains("timeMax=2026-10-18T00%3A00%3A00%2B07%3A00"), "unexpected query: " + eventsQuery.get());
            check("Bearer test-token".equals(authorization.get()), "the call must carry the access token");
            GoogleCalendarClient.RemoteEvent changed = client.changedSince("branch@group.calendar.google.com",
                    java.time.Instant.parse("2026-10-17T00:00:00Z")).stream()
                    .filter(event -> Long.valueOf(42).equals(event.appointmentId())).findFirst().orElseThrow();
            check(changed.end().equals(LocalDateTime.of(2026, 10, 17, 15, 0)), "remote end must survive HTTP parsing");
            check("Remote edited note".equals(changed.description()), "remote note must survive HTTP parsing");
            System.out.println("BUSY_CHECK_OK");
        } finally {
            server.stop(0);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("BUSY_CHECK_FAILED: " + message);
    }
}
