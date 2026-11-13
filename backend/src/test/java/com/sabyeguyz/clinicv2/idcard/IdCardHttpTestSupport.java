package com.sabyeguyz.clinicv2.idcard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.ClinicV2Application;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real JWT/HTTP calls against the application on a disposable PostgreSQL with the v2 demo seed. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(90)
public abstract class IdCardHttpTestSupport {
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected ConfigurableApplicationContext context;
    protected JdbcTemplate db;
    protected ObjectMapper json;
    protected String token;
    protected Long patientId, branchId, actorId;
    private String baseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    protected record HttpResult(int status, JsonNode body) {}

    @BeforeAll
    void startApplication() {
        context = SpringApplication.run(ClinicV2Application.class,
                "--spring.profiles.active=v2", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.locations=classpath:db/migration,classpath:db/seed_v2",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                "--app.jwt.secret=id-card-http-tests-only-secret-at-least-32-characters",
                "--app.calendar.enabled=false", "--logging.level.root=WARN");
        db = context.getBean(JdbcTemplate.class);
        json = context.getBean(ObjectMapper.class);
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
        db.update("update branch set branch_status=true where branch_code in ('01','02')");
        branchId = db.queryForObject("select branch_id from branch where branch_code='01'", Long.class);
        actorId = db.queryForObject("select user_id from app_user where username='manager'", Long.class);
        token = login("manager", "manager123");
        patientId = registerPatient();
    }

    @AfterAll
    void stopApplication() {
        if (context != null) context.close();
    }

    /** One registered patient for the whole class (the demo seed has none); same payload as the Cypress support. */
    private Long registerPatient() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchCode", "01");
        body.put("customerType", "Thai");
        body.put("title", "นาย");
        body.put("firstName", "สมชาย");
        body.put("lastName", "ใจดี");
        body.put("gender", "Male");
        body.put("phone", "0811111111");
        body.put("identityDocumentType", "NATIONAL_ID");
        body.put("identityNumber", "1100000000001");
        body.put("birthDate", "1990-01-01");
        body.put("address", "กรุงเทพฯ");
        HttpResult created = reply("POST", "/api/patients", body);
        Assertions.assertEquals(201, created.status(), created.body().toString());
        return created.body().path("id").asLong();
    }

    protected String login(String username, String password) {
        HttpResult result = replyAs(null, "POST", "/api/auth/login", Map.of("username", username, "password", password));
        Assertions.assertEquals(200, result.status(), result.body().toString());
        return result.body().path("token").asText();
    }

    protected HttpResult reply(String method, String path, Object payload) {
        return replyAs(token, method, path, payload);
    }

    protected HttpResult replyAs(String jwt, String method, String path, Object payload) {
        return replyWithHeaders(jwt, method, path, payload, Map.of());
    }

    protected HttpResult replyWithHeaders(String jwt, String method, String path, Object payload, Map<String, String> headers) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(40));
            if (jwt != null) request.header("Authorization", "Bearer " + jwt);
            request.header("Content-Type", "application/json");
            headers.forEach(request::header);
            request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body().isBlank() ? json.nullNode() : json.readTree(response.body()));
        } catch (Exception exception) {
            throw new AssertionError("HTTP request failed: " + method + " " + path, exception);
        }
    }
}
