package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.ClinicV2Application;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real JWT/HTTP contracts on disposable PostgreSQL; never connects to the main database. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(90)
abstract class CoursePaymentHttpTestSupport {
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    protected ConfigurableApplicationContext context;
    protected JdbcTemplate db;
    protected ObjectMapper json;
    protected String token;
    protected Long patientId, branchId, courseId, actorId;
    private String baseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeAll
    void startHttpApplication() {
        restartApplication();
    }

    protected void restartApplication() {
        if (context != null) context.close();
        context = SpringApplication.run(ClinicV2Application.class,
                "--spring.profiles.active=v2", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.hikari.data-source-properties.options=-c lock_timeout=10000",
                "--spring.flyway.locations=classpath:db/migration,classpath:db/seed_v2",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                "--app.jwt.secret=course-payment-http-tests-only-secret-at-least-32-characters",
                "--app.calendar.enabled=false", "--logging.level.root=WARN");
        db = context.getBean(JdbcTemplate.class);
        json = context.getBean(ObjectMapper.class);
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }

    @BeforeEach
    void resetHttpFixture() {
        db.execute("TRUNCATE TABLE receipt RESTART IDENTITY CASCADE");
        db.execute("TRUNCATE TABLE audit_log");
        db.update("update payment_label set payment_label_status=true");
        db.update("update payment_method set payment_method_status=true where method_name='PromptPay'");
        db.update("update branch set branch_status=true where branch_code in ('01','02')");
        ResourceDatabasePopulator fixture = new ResourceDatabasePopulator(new ClassPathResource("fixtures/shared-course-refund.sql"));
        fixture.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        fixture.execute(context.getBean(javax.sql.DataSource.class));
        patientId = db.queryForObject("select purchaser_patient_id from course order by course_id limit 1", Long.class);
        courseId = db.queryForObject("select course_id from course order by course_id limit 1", Long.class);
        branchId = db.queryForObject("select branch_id from branch where branch_code='01'", Long.class);
        actorId = db.queryForObject("select user_id from app_user where username='manager'", Long.class);
        token = login("manager", "manager123");
    }

    @AfterAll
    void stopHttpApplication() {
        if (context != null) context.close();
    }

    protected record HttpResult(int status, JsonNode body) {}

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
