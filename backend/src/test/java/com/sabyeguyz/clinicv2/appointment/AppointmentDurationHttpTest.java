package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.idcard.IdCardHttpTestSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** AC 010.05/011.04/035.01, using real HTTP validation and PostgreSQL migration constraints. */
class AppointmentDurationHttpTest extends IdCardHttpTestSupport {
    @Test
    void branchAcceptsPositiveMinutesIncludingOneAndBeyondFormerCap() {
        for (int minutes : new int[]{1, 4, 481, 600}) {
            var response = reply("PUT", "/api/branches/" + branchId,
                    Map.of("branchCode", "01", "name", "Duration branch", "address", "Test address",
                            "phone", "0811111111", "defaultAppointmentMinutes", minutes));
            assertEquals(200, response.status(), response.body().toString());
            assertEquals(minutes, response.body().path("defaultAppointmentMinutes").asInt());
            assertEquals(minutes, db.queryForObject("select default_appointment_minutes from branch where branch_id=?",
                    Integer.class, branchId));
        }
        for (int invalid : new int[]{0, -1}) {
            var response = reply("PUT", "/api/branches/" + branchId,
                    Map.of("branchCode", "01", "name", "Duration branch", "address", "Test address",
                            "phone", "0811111111", "defaultAppointmentMinutes", invalid));
            assertEquals(400, response.status(), response.body().toString());
        }
        assertEquals(600, db.queryForObject("select default_appointment_minutes from branch where branch_id=?",
                Integer.class, branchId), "Rejected input must preserve the last valid setting");
    }

    @Test
    void serviceAndCourseAcceptPositiveMinutesAndRejectNonpositiveExplicitValues() {
        for (String route : new String[]{"/api/services", "/api/courses"}) {
            for (int minutes : new int[]{1, 600}) {
                Map<String, Object> body = template(route, "DUR" + (route.endsWith("courses") ? "C" : "S") + minutes);
                body.put("defaultDurationMinutes", minutes);
                var response = reply("POST", route, body);
                assertEquals(201, response.status(), response.body().toString());
                assertEquals(minutes, response.body().path("defaultDurationMinutes").asInt());
                assertEquals(minutes, db.queryForObject("select default_duration_minutes from service where service_id=?",
                        Integer.class, response.body().path("id").asLong()));
            }
            Map<String, Object> updateBody = template(route, route.endsWith("courses") ? "DURUC" : "DURUS");
            updateBody.put("defaultDurationMinutes", 1);
            var createdForUpdate = reply("POST", route, updateBody);
            assertEquals(201, createdForUpdate.status(), createdForUpdate.body().toString());
            long id = createdForUpdate.body().path("id").asLong();
            updateBody.put("defaultDurationMinutes", 600);
            var updated = reply("PUT", route + "/" + id, updateBody);
            assertEquals(200, updated.status(), updated.body().toString());
            assertEquals(600, updated.body().path("defaultDurationMinutes").asInt());
            assertEquals(600, db.queryForObject("select default_duration_minutes from service where service_id=?",
                    Integer.class, id));
            for (int invalid : new int[]{0, -1}) {
                updateBody.put("defaultDurationMinutes", invalid);
                var rejected = reply("PUT", route + "/" + id, updateBody);
                assertEquals(400, rejected.status(), rejected.body().toString());
            }
            assertEquals(600, db.queryForObject("select default_duration_minutes from service where service_id=?",
                    Integer.class, id), "Rejected updates must preserve the last valid duration");
            updateBody.put("defaultDurationMinutes", null);
            var cleared = reply("PUT", route + "/" + id, updateBody);
            assertEquals(200, cleared.status(), cleared.body().toString());
            assertEquals(0, cleared.body().path("defaultDurationMinutes").asInt());
            assertNull(db.queryForObject("select default_duration_minutes from service where service_id=?",
                    Integer.class, id), "No service override must remain NULL so branch duration can apply");
            for (int minutes : new int[]{0, -1}) {
                Map<String, Object> body = template(route, "INVALID" + (route.endsWith("courses") ? "C" : "S") + -minutes);
                body.put("defaultDurationMinutes", minutes);
                var response = reply("POST", route, body);
                assertEquals(400, response.status(), response.body().toString());
            }
            var withoutDuration = reply("POST", route, template(route, route.endsWith("courses") ? "NODURC" : "NODURS"));
            assertEquals(201, withoutDuration.status(), withoutDuration.body().toString());
            assertEquals(0, withoutDuration.body().path("defaultDurationMinutes").asInt());
            assertNull(db.queryForObject("select default_duration_minutes from service where service_id=?",
                    Integer.class, withoutDuration.body().path("id").asLong()));
        }
    }

    private Map<String, Object> template(String route, String code) {
        var body = new LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("name", "Positive duration regression");
        body.put("price", 500);
        if (route.endsWith("courses")) {
            body.put("sessions", 10);
            body.put("validityValue", 1);
            body.put("validityUnit", "Year");
        }
        return body;
    }
}
