package com.sabyeguyz.clinicv2.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Google Calendar REST API over plain HTTP, authorised by a service account key file (recommended) or by a refresh token (OAuth of the clinic's own Gmail
 * account). The service account key can also be pasted in the UI (see {@link CalendarCredentialStore}); the sync only
 * does anything once {@link CalendarSyncService#isEnabled()}.
 */
@Component
public class HttpGoogleCalendarClient implements GoogleCalendarClient {
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final CalendarProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;
    private ServiceAccountCredentials serviceAccount;
    private ServiceAccountCredentials activeAccount;
    private final CalendarCredentialStore store;

    @Autowired
    public HttpGoogleCalendarClient(CalendarProperties properties, ObjectMapper json, CalendarCredentialStore store) {
        this.properties = properties;
        this.json = json;
        this.store = store;
    }

    public HttpGoogleCalendarClient(CalendarProperties properties, ObjectMapper json) {
        this(properties, json, null);
    }

    @Override
    public void verifyCredentials(ServiceAccountCredentials credentials) {
        try {
            requestToken(credentials);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CalendarApiException("Google is not reachable", exception);
        }
    }

    @Override
    public EventRef insert(String calendarId, EventData data) {
        JsonNode created = call("POST", eventsUrl(calendarId), body(data));
        return new EventRef(created.path("id").asText(), created.path("etag").asText());
    }

    @Override
    public EventRef update(String calendarId, String eventId, EventData data) {
        JsonNode updated = call("PUT", eventsUrl(calendarId) + "/" + encode(eventId), body(data));
        return new EventRef(updated.path("id").asText(), updated.path("etag").asText());
    }

    @Override
    public void delete(String calendarId, String eventId) {
        try {
            call("DELETE", eventsUrl(calendarId) + "/" + encode(eventId), null);
        } catch (CalendarApiException exception) {
            if (exception.getStatus() != 404 && exception.getStatus() != 410) throw exception;
        }
    }

    @Override
    public List<RemoteEvent> changedSince(String calendarId, Instant updatedMin) {
        List<RemoteEvent> events = new ArrayList<>();
        String pageToken = null;
        ZoneId zone = ZoneId.of(properties.getTimeZone());
        do {
            String url = eventsUrl(calendarId) + "?showDeleted=true&singleEvents=true&maxResults=250&updatedMin="
                    + encode(updatedMin.toString()) + (pageToken == null ? "" : "&pageToken=" + encode(pageToken));
            JsonNode page = call("GET", url, null);
            for (JsonNode item : page.path("items")) {
                String appointmentId = item.path("extendedProperties").path("private").path("appointmentId").asText("");
                Long id = appointmentId.matches("\\d+") ? Long.valueOf(appointmentId) : null;
                events.add(new RemoteEvent(item.path("id").asText(), item.path("etag").asText(),
                        "cancelled".equals(item.path("status").asText()), start(item.path("start"), zone), id));
            }
            pageToken = page.hasNonNull("nextPageToken") ? page.get("nextPageToken").asText() : null;
        } while (pageToken != null);
        return events;
    }

    @Override
    public List<BusyBlock> busyBlocks(String calendarId, LocalDateTime from, LocalDateTime to) {
        ZoneId zone = ZoneId.of(properties.getTimeZone());
        List<BusyBlock> blocks = new ArrayList<>();
        String pageToken = null;
        do {
            String url = eventsUrl(calendarId) + "?singleEvents=true&orderBy=startTime&maxResults=250"
                    + "&timeMin=" + encode(from.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    + "&timeMax=" + encode(to.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    + (pageToken == null ? "" : "&pageToken=" + encode(pageToken));
            JsonNode page = call("GET", url, null);
            for (JsonNode item : page.path("items")) {
                if ("cancelled".equals(item.path("status").asText())) continue;
                if ("transparent".equals(item.path("transparency").asText())) continue; // shown as Free
                JsonNode privateProperties = item.path("extendedProperties").path("private");
                if (privateProperties.hasNonNull("appointmentId") || "clinic-erp".equals(privateProperties.path("source").asText())) {
                    continue; // written by this system: it is an appointment already
                }
                LocalDateTime start = boundary(item.path("start"), zone);
                LocalDateTime end = boundary(item.path("end"), zone);
                if (start != null && end != null && end.isAfter(start)) blocks.add(new BusyBlock(start, end));
            }
            pageToken = page.hasNonNull("nextPageToken") ? page.get("nextPageToken").asText() : null;
        } while (pageToken != null);
        return blocks;
    }

    /** A start or end of an event: a date-time in the clinic zone, or the start of a day for an all-day event. */
    private static LocalDateTime boundary(JsonNode moment, ZoneId zone) {
        if (moment.hasNonNull("dateTime")) return start(moment, zone);
        if (moment.hasNonNull("date")) return java.time.LocalDate.parse(moment.get("date").asText()).atStartOfDay();
        return null;
    }

    private static LocalDateTime start(JsonNode start, ZoneId zone) {
        if (!start.hasNonNull("dateTime")) return null; // all-day event, never one of ours
        String value = start.get("dateTime").asText();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDateTime();
        } catch (RuntimeException notOffset) {
            return LocalDateTime.parse(value);
        }
    }

    private ObjectNode body(EventData data) {
        ObjectNode event = json.createObjectNode();
        event.put("summary", data.summary());
        event.put("description", data.description());
        if (data.location() != null) event.put("location", data.location());
        event.putObject("start").put("dateTime", data.start().format(LOCAL)).put("timeZone", data.timeZone());
        event.putObject("end").put("dateTime", data.end().format(LOCAL)).put("timeZone", data.timeZone());
        ObjectNode privateProperties = event.putObject("extendedProperties").putObject("private");
        privateProperties.put("appointmentId", String.valueOf(data.appointmentId()));
        privateProperties.put("source", "clinic-erp");
        event.putObject("reminders").put("useDefault", false);
        return event;
    }

    private String eventsUrl(String calendarId) {
        return properties.getApiBaseUrl() + "/calendars/" + encode(calendarId) + "/events";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode call(String method, String url, ObjectNode body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token()).header("Accept", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new CalendarApiException(response.statusCode(), "Google Calendar " + method + " answered " + response.statusCode());
            }
            return response.body() == null || response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CalendarApiException("Google Calendar is not reachable", exception);
        }
    }

    private synchronized String token() throws IOException, InterruptedException {
        ServiceAccountCredentials account = activeServiceAccount();
        if (account != activeAccount) { // the key changed (pasted or removed in the UI): forget the old token
            activeAccount = account;
            accessToken = null;
            accessTokenExpiresAt = Instant.EPOCH;
        }
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(60))) return accessToken;
        JsonNode token = requestToken(account);
        accessToken = token.path("access_token").asText();
        accessTokenExpiresAt = Instant.now().plusSeconds(token.path("expires_in").asLong(3600));
        return accessToken;
    }

    /** The service account to sign in with (key file, else the key saved in the UI), or null to use the OAuth refresh token. */
    private ServiceAccountCredentials activeServiceAccount() {
        if (!properties.getServiceAccountKeyFile().isBlank()) {
            if (serviceAccount == null) {
                serviceAccount = ServiceAccountCredentials.load(java.nio.file.Path.of(properties.getServiceAccountKeyFile()), json);
            }
            return serviceAccount;
        }
        ServiceAccountCredentials stored = store == null ? null : store.current().orElse(null);
        if (stored != null) return stored;
        if (properties.getClientId().isBlank() || properties.getRefreshToken().isBlank()) {
            throw new CalendarApiException(0, "Google Calendar is not connected yet");
        }
        return null;
    }

    /** Signs in to Google as the service account (a signed JWT, nothing that expires) or with the refresh token. */
    private JsonNode requestToken(ServiceAccountCredentials account) throws IOException, InterruptedException {
        String form;
        String tokenUrl = properties.getTokenUrl();
        if (account != null) {
            tokenUrl = account.tokenUri();
            form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                    + "&assertion=" + encode(account.assertion("https://www.googleapis.com/auth/calendar", Instant.now()));
        } else {
            form = "client_id=" + encode(properties.getClientId()) + "&client_secret=" + encode(properties.getClientSecret())
                    + "&refresh_token=" + encode(properties.getRefreshToken()) + "&grant_type=refresh_token";
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new CalendarApiException(response.statusCode(), "Google sign-in was refused (" + response.statusCode() + ")");
        }
        return json.readTree(response.body());
    }
}
