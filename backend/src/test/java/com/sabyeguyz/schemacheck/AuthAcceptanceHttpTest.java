package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP and persisted assertions for US001/005; no browser/logout claims. */
class AuthAcceptanceHttpTest extends CoursePaymentHttpTestSupport {
    private Map<String,Object> account() {
        String suffix = UUID.randomUUID().toString().substring(0,8);
        var data = new LinkedHashMap<String,Object>();
        data.put("username", "ac-" + suffix);
        data.put("email", "ac-" + suffix + "@example.test");
        data.put("password", "Synthetic-test-password-123");
        data.put("role", "THERAPIST");
        data.put("fullName", "Synthetic Account");
        data.put("branchCode", "01");
        return data;
    }

    private JsonNode create(Map<String,Object> request) {
        var response = reply("POST", "/api/users", request);
        assertEquals(201, response.status(), response.body().toString());
        return response.body();
    }

    private HttpResult authenticate(String identity, String password) {
        return replyAs(null, "POST", "/api/auth/login", Map.of("username", identity, "password", password));
    }

    @Test
    void usernameAndCaseInsensitiveEmailResolveSameAccountAndPersistEachLoginTime() {
        var request = account(); var user = create(request); long id = user.path("id").asLong();
        for (String identity : new String[]{request.get("username").toString(), request.get("email").toString().toUpperCase(java.util.Locale.ROOT)}) {
            db.update("update app_user set last_login_at=timestamp '2000-01-01 00:00:00' where user_id=?", id);
            var response = authenticate(identity, request.get("password").toString());
            assertEquals(200, response.status(), response.body().toString());
            assertEquals(id, response.body().path("id").asLong());
            assertEquals("THERAPIST", response.body().path("role").asText());
            assertEquals("01", response.body().path("branchCode").asText());
            assertFalse(response.body().path("token").asText().isBlank());
            assertEquals(1, db.queryForObject("select count(*) from app_user where user_id=? and last_login_at > current_timestamp - interval '1 minute'", Integer.class, id));
            assertEquals(403, replyAs(response.body().path("token").asText(), "GET", "/api/users", null).status());
        }
    }

    @Test
    void wrongPasswordAndInactiveAccountDoNotRecordLoginOrKeepOldTokenAccess() {
        var request = account(); var user = create(request); long id = user.path("id").asLong();
        String jwt = login(request.get("username").toString(), request.get("password").toString());
        db.update("update app_user set last_login_at=timestamp '2000-01-01 00:00:00' where user_id=?", id);
        assertEquals(401, authenticate(request.get("username").toString(), "wrong-password").status());
        assertEquals(204, reply("DELETE", "/api/users/" + id, null).status());
        assertTrue(java.util.Set.of(400,401,403).contains(authenticate(request.get("username").toString(), request.get("password").toString()).status()));
        assertEquals(401, replyAs(jwt, "GET", "/api/branches", null).status());
        assertEquals(1, db.queryForObject("select count(*) from app_user where user_id=? and last_login_at=timestamp '2000-01-01 00:00:00' and user_status=false", Integer.class, id));
    }

    @Test
    void accountCreationHashesPasswordAndStoresOneRoleMatchingStaffPosition() {
        var request = account(); var user = create(request); long id = user.path("id").asLong();
        String hash = db.queryForObject("select password_hash from app_user where user_id=?", String.class, id);
        assertNotEquals(request.get("password"), hash);
        assertTrue(context.getBean(PasswordEncoder.class).matches(request.get("password").toString(), hash));
        assertEquals(1, db.queryForObject("select count(*) from user_role where user_id=?", Integer.class, id));
        assertEquals("THERAPIST", user.path("role").asText());
        assertFalse(user.has("password")); assertFalse(user.has("passwordHash"));
        request.remove("password"); request.put("role", "MANAGER");
        var changed = reply("PUT", "/api/users/" + id, request);
        assertEquals(200, changed.status(), changed.body().toString());
        assertEquals("MANAGER", changed.body().path("role").asText());
        assertEquals(1, db.queryForObject("select count(*) from user_role where user_id=?", Integer.class, id));
        assertEquals("Manager", db.queryForObject("select position from staff s join app_user u using(staff_id) where u.user_id=?", String.class, id));
    }

    @Test
    void duplicateUsernameAndCaseInsensitiveEmailAndMalformedEmailRollbackWholeCreateOrUpdate() {
        var first = account(); var firstUser = create(first);
        var second = account(); long secondId = create(second).path("id").asLong();
        for (String field : new String[]{"username", "email"}) {
            var invalid = account(); invalid.put(field, first.get(field).toString().toUpperCase(java.util.Locale.ROOT));
            // Username uniqueness is exact; use the exact occupied username, email deliberately differs in case.
            if (field.equals("username")) invalid.put(field, first.get(field));
            int usersBefore = db.queryForObject("select count(*) from app_user", Integer.class);
            int staffBefore = db.queryForObject("select count(*) from staff", Integer.class);
            assertEquals(400, reply("POST", "/api/users", invalid).status());
            assertEquals(usersBefore, db.queryForObject("select count(*) from app_user", Integer.class));
            assertEquals(staffBefore, db.queryForObject("select count(*) from staff", Integer.class));
            var update = new LinkedHashMap<>(second); update.remove("password"); update.put(field, invalid.get(field));
            assertEquals(400, reply("PUT", "/api/users/" + secondId, update).status());
            assertEquals(second.get(field), db.queryForObject("select " + field + " from app_user where user_id=?", String.class, secondId));
        }
        var invalid = account(); invalid.put("email", "malformed-address");
        int usersBefore = db.queryForObject("select count(*) from app_user", Integer.class);
        int staffBefore = db.queryForObject("select count(*) from staff", Integer.class);
        assertEquals(400, reply("POST", "/api/users", invalid).status());
        assertEquals(usersBefore, db.queryForObject("select count(*) from app_user", Integer.class));
        assertEquals(staffBefore, db.queryForObject("select count(*) from staff", Integer.class));
        var update = new LinkedHashMap<>(second); update.remove("password"); update.put("email", "malformed-address");
        assertEquals(400, reply("PUT", "/api/users/" + secondId, update).status());
        assertEquals(second.get("email"), db.queryForObject("select email from app_user where user_id=?", String.class, secondId));
        assertTrue(firstUser.path("id").asLong() > 0);
    }

    @Test
    void validProfileEditPreservesUserStaffAndHistoricalAuditReferences() {
        var request = account(); long id = create(request).path("id").asLong();
        Long staffId = db.queryForObject("select staff_id from app_user where user_id=?", Long.class, id);
        var historical = db.queryForList("select * from audit_log where table_name='app_user' and record_id=? order by audit_id", String.valueOf(id));
        assertFalse(historical.isEmpty());
        request.put("username", request.get("username") + "-new");
        request.put("email", "new-" + request.get("email")); request.remove("password");
        var response = reply("PUT", "/api/users/" + id, request);
        assertEquals(200, response.status(), response.body().toString());
        assertEquals(id, response.body().path("id").asLong());
        assertEquals(staffId, db.queryForObject("select staff_id from app_user where user_id=?", Long.class, id));
        for (var row : historical) assertEquals(row, db.queryForMap("select * from audit_log where audit_id=?", row.get("audit_id")));
        assertEquals(200, authenticate(request.get("username").toString(), "Synthetic-test-password-123").status());
        assertEquals(200, authenticate(request.get("email").toString(), "Synthetic-test-password-123").status());
    }
}
