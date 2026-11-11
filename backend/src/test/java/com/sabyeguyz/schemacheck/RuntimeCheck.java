package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.ClinicV2Application;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Runs the production entry point, Flyway and real HTTP contracts on a disposable PostgreSQL DB. */
public class RuntimeCheck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static String base;
    private static String token;

    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(ClinicV2Application.class);
        try (var context = (ServletWebServerApplicationContext) app.run(
                "--spring.profiles.active=v2", "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=" + required("DB_URL"),
                "--spring.datasource.username=" + required("DB_USER"),
                "--spring.datasource.password=" + required("DB_PASSWORD"),
                "--app.jwt.secret=runtime-check-only-secret-0123456789-0123456789",
                "--app.bootstrap.manager-username=runtime-manager",
                "--app.bootstrap.manager-email=runtime-manager@example.com",
                "--app.bootstrap.manager-password=runtime-test-password")) {
            base = "http://127.0.0.1:" + context.getWebServer().getPort();
            JdbcTemplate db = context.getBean(JdbcTemplate.class);
            check(db.queryForObject("select count(*) from flyway_schema_history where success and version in "
                            + "('1','2','3','4','1001','1002','1003','1004','1005','1006','1007')", Integer.class) == 11,
                    "V1-V4 and V1001-V1007 must migrate");
            check(db.queryForObject("select count(*) from information_schema.columns where table_name='appointment' and column_name='appointment_end_at'", Integer.class) == 1,
                    "appointment end time from V1007 must exist");
            check(db.queryForObject("select count(*) from pg_enum e join pg_type t on t.oid=e.enumtypid "
                    + "where t.typname='transaction_type_enum' and e.enumlabel='Assessment'", Integer.class) == 1,
                    "historical Assessment enum must exist on a fresh production database");
            check(context.getBeansOfType(com.sabyeguyz.clinic.appointment.AppointmentController.class).isEmpty(),
                    "legacy controller must not be scanned");
            check(context.getBeansOfType(com.sabyeguyz.clinicv2.appointment.AppointmentController.class).size() == 1,
                    "v2 controller must be active");
            check(db.queryForObject("select count(*) from app_user where username='runtime-manager'", Integer.class) == 1,
                    "fresh runtime provisions the configured first manager");

            JsonNode invalid = request("POST", "/api/auth/login",
                    Map.of("username", "does-not-exist", "password", "invalid-password"), 401);
            check(invalid.path("message").asText().equals("ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง"),
                    "frontend invalid-login message contract");
            request("GET", "/api/branches", null, 401);

            Long userId = db.queryForObject("select user_id from app_user where username='runtime-manager'", Long.class);
            JsonNode login = request("POST", "/api/auth/login",
                    Map.of("username", "runtime-manager", "password", "runtime-test-password"), 200);
            token = login.path("token").asText();
            check(!token.isBlank() && login.path("role").asText().equals("MANAGER"), "frontend manager role contract");

            JsonNode branch = request("POST", "/api/branches",
                    Map.of("branchCode", "99", "name", "Runtime branch", "address", "Test address", "phone", "020000000"), 201);
            request("GET", "/api/branches", null, 200);
            JsonNode singleService = request("POST", "/api/services",
                    Map.of("code", "RUNTIME", "name", "Runtime service", "price", 500, "defaultDurationMinutes", 30), 201);
            request("GET", "/api/services", null, 200);
            JsonNode paymentMethod = request("POST", "/api/payment-methods",
                    Map.of("code", "PromptPay", "name", "PromptPay"), 409);
            request("GET", "/api/payment-methods", null, 200);
            JsonNode patient = request("POST", "/api/patients", Map.of(
                    "branchCode", "01", "customerType", "Thai", "title", "นาย", "firstName", "Runtime",
                    "lastName", "Patient", "gender", "Male", "phone", "0810000000",
                    "identityNumber", "1234567890123"), 201);
            request("GET", "/api/patients/" + patient.path("id").asLong(), null, 200);
            request("GET", "/api/patients", null, 200);
            JsonNode template = request("POST", "/api/courses", Map.of("code", "DAYS", "name", "Day course", "price", 1000,
                    "sessions", 6, "bonusSessions", 1, "validityDays", 30), 201);
            request("GET", "/api/courses", null, 200);
            // A single visit is treated by one PT: create the staff member first and name them in the sale.
            JsonNode seller = request("POST", "/api/users", Map.of("username", "runtime-seller",
                    "email", "Runtime.Seller@example.com", "password", "runtime-test-password",
                    "role", "MANAGER", "employeeCode", "RUNTIME001", "fullName", "Runtime Seller", "branchCode", "01"), 201);
            check(seller.path("email").asText().equals("Runtime.Seller@example.com"), "V4 email must be returned");
            check(db.queryForObject("select email from app_user where username='runtime-seller'", String.class)
                    .equals("Runtime.Seller@example.com"), "V4 email must persist");
            Long salespersonStaffId = db.queryForObject("select staff_id from staff where staff_code='RUNTIME001'", Long.class);
            db.update("insert into permission(permission_name,permission_create_by) values ('RUNTIME_COMMISSION',?)", userId);
            Long permissionId = db.queryForObject("select permission_id from permission where permission_name='RUNTIME_COMMISSION'", Long.class);
            db.update("insert into commission_scheme(scheme_name,scheme_version,permission_id,effective_from,scheme_create_by) "
                    + "values ('Runtime scheme',1,?,date '2000-01-01',?)", permissionId, userId);
            Long schemeId = db.queryForObject("select commission_scheme_id from commission_scheme where scheme_name='Runtime scheme'", Long.class);
            db.update("insert into commission_tier(commission_scheme_id,permission_id,tier_order,minimum_monthly_sales,"
                    + "commission_rate,effective_from,tier_create_by) values (?,?,1,0,0.1,date '2000-01-01',?)", schemeId, permissionId, userId);
            JsonNode sale = request("POST", "/api/payments", Map.of(
                    "patientId", patient.path("id").asLong(), "branchCode", "01",
                    "salespersonStaffId", salespersonStaffId,
                    "items", java.util.List.of(Map.of("type", "COURSE", "referenceId", template.path("id").asLong(),
                            "quantity", 1)),
                    "paymentLabelCode", "FULL",
                    "payments", java.util.List.of(Map.of("paymentMethodCode", "PromptPay", "amount", 1000))), 201);
            check(sale.path("receiptNo").asText().matches("RE[0-9]{8}"), "latest receipt number format");
            check(db.queryForObject("select r.payment_label::text from receipt r join sales_transaction s "
                    + "on s.receipt_id=r.receipt_id where s.transaction_id=?", String.class, sale.path("id").asLong()).equals("FullyPaid"),
                    "full payment label must persist");
            request("GET", "/api/receipts/" + sale.path("id").asLong(), null, 200);
            request("GET", "/api/payments", null, 200);
            JsonNode sellerLogin = request("POST", "/api/auth/login",
                    Map.of("username", "runtime.seller@EXAMPLE.com", "password", "runtime-test-password"), 200);
            check(sellerLogin.path("email").asText().equals("Runtime.Seller@example.com"), "V4 email login response");
            token = sellerLogin.path("token").asText();
            JsonNode updatedSeller = request("PUT", "/api/users/" + seller.path("id").asLong(),
                    Map.of("username", "runtime-seller", "email", "seller.updated@example.com",
                            "password", "runtime-test-password", "role", "MANAGER", "employeeCode", "RUNTIME001",
                            "fullName", "Runtime Seller", "branchCode", "01"), 200);
            check(updatedSeller.path("email").asText().equals("seller.updated@example.com"), "V4 email update response");
            token = null; // Profile changes invalidate the existing JWT before login probes.
            request("POST", "/api/auth/login",
                    Map.of("username", "Runtime.Seller@example.com", "password", "runtime-test-password"), 401);
            token = request("POST", "/api/auth/login",
                    Map.of("username", "seller.updated@example.com", "password", "runtime-test-password"), 200)
                    .path("token").asText();
            JsonNode purchase = request("POST", "/api/course-purchases", Map.of("patientId", patient.path("id").asLong(),
                    "courseId", template.path("id").asLong(), "branchCode", "01", "paymentLabelCode", "FULL"), 201);
            long purchaseId = purchase.path("id").asLong();
            check(purchase.path("purchased").asInt() == 6 && purchase.path("sessions").asInt() == 6
                            && purchase.path("bonusSessions").asInt() == 0,
                    "course entitlement includes paid and promotional visits exactly once");
            check(db.queryForObject("select total_visits from course where course_id=?", Integer.class, purchaseId) == 6,
                    "generated entitlement must equal purchased balance after reload");
            JsonNode cancelled = request("POST", "/api/course-purchases", Map.of("patientId", patient.path("id").asLong(),
                    "courseId", template.path("id").asLong(), "branchCode", "01", "paymentLabelCode", "FULL"), 201);
            long cancelledId = cancelled.path("id").asLong();
            request("POST", "/api/commission/closings/" + java.time.YearMonth.now()
                    + "?branchCode=01", Map.of(), 200);
            for (int visit = 0; visit < 6; visit++) {
                var startAt = java.time.LocalDate.now().plusDays(1).atTime(10, 0)
                        .atOffset(java.time.ZoneOffset.ofHours(7)).plusMinutes(visit * 30L);
                JsonNode appointment = request("POST", "/api/appointments", Map.of(
                        "branchCode", "01", "patientId", patient.path("id").asLong(),
                        "therapistId", salespersonStaffId, "serviceId", singleService.path("id").asLong(),
                        "startAt", startAt.toString(), "endAt", startAt.plusMinutes(30).toString()), 201);
                request("POST", "/api/course-usages", Map.of("coursePurchaseId", purchaseId,
                        "appointmentId", appointment.path("id").asLong(),
                        "patientId", patient.path("id").asLong(), "treatingStaffId", salespersonStaffId), 200);
            }
            check(db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, purchaseId) == 0,
                    "all paid and bonus visits persist");
            check(db.queryForObject("select commission_outstanding from course where course_id=?", java.math.BigDecimal.class, purchaseId)
                    .compareTo(java.math.BigDecimal.ZERO) == 0, "last bonus visit cannot exceed the commission pool");
            request("POST", "/api/course-purchases/" + cancelledId + "/cancel", Map.of("reason", "Runtime cancel"), 200);
            var cancelledUseAt = java.time.LocalDate.now().plusDays(1).atTime(14, 0)
                    .atOffset(java.time.ZoneOffset.ofHours(7));
            JsonNode cancelledUseAppointment = request("POST", "/api/appointments", Map.of(
                    "branchCode", "01", "patientId", patient.path("id").asLong(),
                    "therapistId", salespersonStaffId, "serviceId", singleService.path("id").asLong(),
                    "startAt", cancelledUseAt.toString(), "endAt", cancelledUseAt.plusMinutes(30).toString()), 201);
            request("POST", "/api/course-usages", Map.of("coursePurchaseId", cancelledId,
                    "appointmentId", cancelledUseAppointment.path("id").asLong(),
                    "patientId", patient.path("id").asLong(), "treatingStaffId", salespersonStaffId), 400);
            check(db.queryForObject("select count(*) from course_visit_usage where course_id=?", Integer.class, cancelledId) == 0,
                    "cancelled course cannot write usage records");
            JsonNode balanceReport = request("GET", "/api/reports/course-balances?from=2000-01-01&to=2100-12-31", null, 200);
            check(balanceReport.path("rows").size() == 3, "balance report contains all three purchases");
            for (JsonNode row : balanceReport.path("rows")) {
                check(row.path("purchased").asInt() == 6, "report cannot count bonus visits twice");
            }
            check(db.queryForObject("select service_type::text from service where service_code='RUNTIME'", String.class)
                    .equals("SingleVisit"), "named-enum service insert persisted");

            // Latest value/unit semantics must persist and retain calendar-month behavior.
            var services = context.getBean(com.sabyeguyz.clinicv2.catalog.ServiceRepository.class);
            var course = new com.sabyeguyz.clinicv2.catalog.Service();
            course.setServiceCode("MONTHLY"); course.setServiceName("Monthly course");
            course.setServiceType(com.sabyeguyz.clinicv2.enums.ServiceType.Course);
            course.setServicePrice(java.math.BigDecimal.TEN); course.setDefaultTotalVisits(5);
            course.setDefaultValidityValue(1);
            course.setDefaultValidityUnit(com.sabyeguyz.clinicv2.enums.ValidityUnit.Month);
            course.setServiceCreateAt(java.time.Instant.now()); course.setServiceCreateBy(userId);
            Long courseId = services.saveAndFlush(course).getId();
            var loaded = services.findById(courseId).orElseThrow();
            check(loaded.expiryFrom(LocalDate.of(2026, 1, 31)).equals(LocalDate.of(2026, 2, 28)),
                    "calendar month validity survives reload");
            check(loaded.getDefaultValidityDays() == null, "months cannot be silently converted to days");
            check(db.queryForObject("select last_login_at is not null from app_user where user_id=?", Boolean.class, userId),
                    "login timestamp persisted in v2 app_user");
            check(branch.path("branchCode").asText().equals("99"), "v2 branch response");
            // A normal therapist must retain the same authorization limits as the legacy API.
            db.update(
                    "insert into app_user(username,email,password_hash) values (?,?,?)",
                    "runtime-therapist",
                    "runtime-therapist@example.com",
                    new BCryptPasswordEncoder().encode("runtime-test-password"));
            Long therapistId = db.queryForObject("select user_id from app_user where username='runtime-therapist'", Long.class);
            db.update("insert into user_role(user_id,role_id) select ?,role_id from role where role_name='Therapist'", therapistId);
            token = request("POST", "/api/auth/login", Map.of("username", "runtime-therapist", "password", "runtime-test-password"), 200)
                    .path("token").asText();
            request("GET", "/api/users", null, 403);
            request("POST", "/api/auth/register", Map.of("username", "blocked",
                    "email", "blocked@example.com", "password", "runtime-test-password"), 403);
            request("POST", "/api/services", Map.of("code", "BLOCKED", "name", "Blocked", "price", 10), 403);
            check(db.queryForObject("select count(*) from service where service_code='BLOCKED'", Integer.class) == 0,
                    "forbidden writes cannot mutate the database");
            System.out.println("RUNTIME_CHECK_OK");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name);
        return value;
    }

    private static JsonNode request(String method, String path, Object body, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == expected,
                method + " " + path + " expected " + expected + " got " + response.statusCode() + ": " + response.body());
        return response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
