package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.course.*;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** US-068..071: real PostgreSQL/JWT HTTP proof of frozen cumulative course documents. */
class CourseUsagePrintHttpTest extends CoursePaymentHttpTestSupport {
    private long usageId;
    private long printId;

    @BeforeEach
    void createTwoSessions() {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var course = context.getBean(CourseRepository.class).findById(courseId).orElseThrow();
            var service = context.getBean(CourseVisitUsageService.class);
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
            for (int day : List.of(1, 2)) {
                PatientVisit visit = new PatientVisit();
                visit.setPatient(course.getPurchaserPatient());
                visit.setBranch(course.getBranch());
                visit.setAssignedStaff(course.getSellerStaff());
                visit.setTreatingStaff(course.getSellerStaff());
                visit.setVisitStatus(VisitStatus.Completed);
                visit.setVisitDate(Instant.parse("2026-10-0" + day + "T03:00:00Z"));
                visit.setCheckoutTime(Instant.parse("2026-10-0" + day + "T04:00:00Z"));
                visit.setVisitCreateAt(Instant.now());
                visit.setVisitCreateBy(actorId);
                em.persist(visit);
                var usage = service.use(course, visit, course.getPurchaserPatient(), course.getSellerStaff(),
                        LocalDate.of(2026, 10, day), actorId);
                usageId = usage.getId();
            }
        });
        printId = db.queryForObject("select print_id from course_usage_print where visit_usage_id=?", Long.class, usageId);
    }

    private JsonNode snapshot(JsonNode print) throws Exception {
        return json.readTree(print.path("documentSnapshot").asText());
    }

    @Test
    void usageTransactionsExposeSourceBackedFieldsAndFilterWithoutChangingRights() {
        int remaining = db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId);
        int movements = db.queryForObject("select count(*) from course_ledger where course_id=?", Integer.class, courseId);
        var all = reply("GET", "/api/course-usages", null);
        assertEquals(200, all.status(), all.body().toString());
        var row = all.body().get(0);
        assertEquals(courseId, row.path("coursePurchaseId").asLong());
        assertFalse(row.path("courseNo").asText().isBlank());
        assertEquals("2026-10-02", row.path("visitDate").asText());
        assertEquals("Completed", row.path("usageStatus").asText());
        assertEquals(patientId.longValue(), db.queryForObject(
                "select patient_id from course_visit_usage where visit_usage_id=?", Long.class, usageId));
        assertFalse(row.path("usingPatientName").asText().isBlank());
        assertFalse(row.path("usingPatientHn").asText().isBlank());
        assertEquals(1, row.path("quantity").asInt());
        assertEquals("01", row.path("branchCode").asText());
        assertEquals("2026-10-02", reply("GET", "/api/course-usages?from=2026-10-02&to=2026-10-02&patient="
                + row.path("usingPatientHn").asText(), null).body().get(0).path("visitDate").asText());
        assertEquals(0, reply("GET", "/api/course-usages?from=2026-10-03&to=2026-10-03", null).body().size());
        assertEquals(remaining, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        assertEquals(movements, db.queryForObject("select count(*) from course_ledger where course_id=?", Integer.class, courseId));
    }

    @Test
    void usageReversalPersistsTheSelectedCancellationReasonCode() {
        long movementId = db.queryForObject(
                "select ledger_id from course_ledger where visit_usage_id=? and entry_type='Treatment'",
                Long.class, usageId);
        var result = reply("POST", "/api/course-movements/" + movementId + "/reverse",
                Map.of("reasonCode", "WrongEntryOrService", "reason", "Incorrect usage entry"));
        assertEquals(200, result.status(), result.body().toString());
        assertEquals("WrongEntryOrService", db.queryForObject(
                "select reason_code::text from transaction_cancellation where visit_usage_id=? order by cancellation_id desc limit 1",
                String.class, usageId));
    }

    @Test
    void storesCompleteCumulativeDocumentAndKeepsItFrozenAcrossDataChanges() throws Exception {
        var original = reply("GET", "/api/course-usage-prints/" + printId, null);
        assertEquals(200, original.status(), original.body().toString());
        var snapshot = snapshot(original.body());
        assertEquals(10, snapshot.path("totalVisits").asInt());
        assertEquals(8, snapshot.path("remainingVisits").asInt());
        assertEquals(2, snapshot.path("visitsUsed").asInt());
        assertEquals(patientId.longValue(), snapshot.path("buyer").path("id").asLong());
        assertFalse(snapshot.path("buyer").path("hn").asText().isBlank());
        assertFalse(snapshot.path("buyer").path("name").asText().isBlank());
        assertEquals("01", snapshot.path("branch").path("code").asText());
        assertFalse(snapshot.path("purchaseDate").asText().isBlank());
        assertFalse(snapshot.path("expiryDate").asText().isBlank());
        assertEquals(original.body().path("issuedAt").asText(), snapshot.path("issuedAt").asText());
        assertEquals(2, snapshot.path("history").size());
        assertEquals(1, snapshot.path("history").get(0).path("quantity").asInt());
        assertFalse(snapshot.path("history").get(0).path("treatingStaffName").asText().isBlank());
        assertFalse(snapshot.path("history").get(1).path("patientHn").asText().isBlank());
        assertEquals(3, snapshot.path("movements").size());
        String frozen = original.body().path("documentSnapshot").asText();
        db.update("update course set course_name='Changed after issue',expiry_date='2098-12-31' where course_id=?", courseId);
        db.update("update branch set branch_name='Changed branch after issue' where branch_id=?", branchId);
        db.update("update patient set patient_fname='Changed after issue' where patient_id=?", patientId);
        var reread = reply("GET", "/api/course-usage-prints/" + printId, null);
        assertEquals(frozen, reread.body().path("documentSnapshot").asText());
    }

    @Test
    void reprintRecordsEventWithoutCreatingVersionOrChangingRights() {
        int rights = db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId);
        var before = reply("GET", "/api/course-usage-prints/" + printId, null).body();
        var reprint = reply("POST", "/api/course-usage-prints/" + printId + "/reprints", Map.of());
        assertEquals(200, reprint.status(), reprint.body().toString());
        assertEquals(before.path("documentSnapshot"), reprint.body().path("documentSnapshot"));
        assertEquals(1, reprint.body().path("version").asInt());
        assertEquals(2, reprint.body().path("events").size());
        assertEquals("Reprint", reprint.body().path("events").get(1).path("type").asText());
        assertEquals(rights, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        assertEquals(1, db.queryForObject("select count(*) from course_usage_print where visit_usage_id=?", Integer.class, usageId));
    }

    @Test
    void revisionExplainsReturnedRightsAndKeepsOldDocument() throws Exception {
        String frozen = reply("GET", "/api/course-usage-prints/" + printId, null).body().path("documentSnapshot").asText();
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var usage = context.getBean(CourseVisitUsageRepository.class).findByCourseIdOrderByIdDesc(courseId).stream()
                    .filter(row -> row.getId() != usageId).findFirst().orElseThrow();
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
            var permission = em.createQuery("select p from Permission p where p.permissionName='COURSE_REVERSE'", Permission.class).getSingleResult();
            context.getBean(CourseVisitUsageService.class).reverse(usage, permission, "Incorrect session", actorId);
        });
        var revised = reply("POST", "/api/course-usages/" + usageId + "/print-revisions", Map.of());
        assertEquals(200, revised.status(), revised.body().toString());
        assertEquals(2, revised.body().path("version").asInt());
        var contents = snapshot(revised.body());
        assertEquals(9, contents.path("remainingVisits").asInt());
        assertEquals(2, contents.path("history").size(), "Reversed treatment must remain explained");
        assertTrue(contents.path("history").toString().contains("Reversed"));
        assertTrue(contents.path("movements").toString().contains("Reversal"));
        assertTrue(contents.path("movements").toString().contains("Incorrect session"));
        assertEquals(frozen, reply("GET", "/api/course-usage-prints/" + printId, null).body().path("documentSnapshot").asText());
    }

    @Test
    void concurrentRevisionsHaveDistinctVersionsAndLeaveRightsUnchanged() throws Exception {
        int before = db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(index -> pool.submit(() -> {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                return reply("POST", "/api/course-usages/" + usageId + "/print-revisions", Map.of());
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            var first = calls.get(0).get(40, TimeUnit.SECONDS);
            var second = calls.get(1).get(40, TimeUnit.SECONDS);
            assertEquals(200, first.status(), first.body().toString());
            assertEquals(200, second.status(), second.body().toString());
            assertEquals(java.util.Set.of(2, 3), java.util.Set.of(first.body().path("version").asInt(), second.body().path("version").asInt()));
            assertEquals(before, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void readableDistinctActorsAndOldPrintHistoryRemainAvailableAfterRevisionWithoutConsumingRights() throws Exception {
        long therapistId = db.queryForObject("select user_id from app_user where username='therapist'", Long.class);
        String therapistName = db.queryForObject("select trim(concat_ws(' ',s.staff_fname,s.staff_lname)) from app_user u join staff s on s.staff_id=u.staff_id where u.user_id=?", String.class, therapistId);
        String managerName = db.queryForObject("select trim(concat_ws(' ',s.staff_fname,s.staff_lname)) from app_user u join staff s on s.staff_id=u.staff_id where u.user_id=?", String.class, actorId);
        int rights = db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId);
        int usageCount = db.queryForObject("select count(*) from course_visit_usage where course_id=?", Integer.class, courseId);
        int movementCount = db.queryForObject("select count(*) from course_ledger where course_id=?", Integer.class, courseId);
        String frozen = reply("GET", "/api/course-usage-prints/" + printId, null).body().path("documentSnapshot").asText();
        String therapistToken = login("therapist", "therapist123");
        var copied = replyAs(therapistToken, "POST", "/api/course-usage-prints/" + printId + "/reprints", Map.of());
        assertEquals(200, copied.status(), copied.body().toString());
        var oldBeforeRevision = reply("GET", "/api/course-usage-prints/" + printId, null).body();
        assertEquals(actorId.longValue(), oldBeforeRevision.path("issuedById").asLong());
        assertEquals(managerName, oldBeforeRevision.path("issuedBy").asText());
        assertEquals(2, oldBeforeRevision.path("events").size());
        var issued = oldBeforeRevision.path("events").get(0);
        var reprinted = oldBeforeRevision.path("events").get(1);
        assertEquals("Issued", issued.path("type").asText());
        assertEquals(actorId.longValue(), issued.path("printedById").asLong());
        assertEquals(managerName, issued.path("printedBy").asText());
        assertEquals("Reprint", reprinted.path("type").asText());
        assertEquals(therapistId, reprinted.path("printedById").asLong());
        assertEquals(therapistName, reprinted.path("printedBy").asText());
        for (JsonNode event : oldBeforeRevision.path("events")) {
            assertEquals(printId, event.path("printId").asLong());
            assertEquals(1, event.path("version").asInt());
            assertNotNull(Instant.parse(event.path("printedAt").asText()));
        }
        // A revision reflects current document data; the historical frozen version and its print events stay untouched.
        db.update("update course set course_name='Revised name for document history' where course_id=?", courseId);
        var revised = reply("POST", "/api/course-usages/" + usageId + "/print-revisions", Map.of());
        assertEquals(200, revised.status(), revised.body().toString());
        assertEquals(2, revised.body().path("version").asInt());
        assertNotEquals(printId, revised.body().path("id").asLong());
        assertEquals("Revised name for document history", snapshot(revised.body()).path("courseName").asText());
        assertEquals(2, snapshot(revised.body()).path("history").size());
        var revisedEvent = revised.body().path("events").get(0);
        assertEquals("Issued", revisedEvent.path("type").asText());
        assertEquals(actorId.longValue(), revisedEvent.path("printedById").asLong());
        assertEquals(managerName, revisedEvent.path("printedBy").asText());
        assertEquals(2, revisedEvent.path("version").asInt());
        assertEquals(revised.body().path("id"), revisedEvent.path("printId"));
        var oldAfterRevision = reply("GET", "/api/course-usage-prints/" + printId, null).body();
        assertEquals(oldBeforeRevision, oldAfterRevision, "Revision must not rewrite old events, IDs, timestamps or frozen document");
        assertEquals(frozen, oldAfterRevision.path("documentSnapshot").asText());
        var library = reply("GET", "/api/course-purchases/" + courseId + "/usage-prints", null);
        assertEquals(200, library.status(), library.body().toString());
        boolean foundOld = false, foundNew = false;
        for (JsonNode document : library.body()) {
            if (document.path("id").asLong() == printId) {
                foundOld = true;
                assertEquals(oldAfterRevision, document);
            }
            if (document.path("id").asLong() == revised.body().path("id").asLong()) {
                foundNew = true;
                assertEquals(2, document.path("version").asInt());
                assertEquals(1, document.path("events").size());
            }
        }
        assertTrue(foundOld && foundNew, "Both original and revision must remain independently inspectable");
        assertEquals(rights, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        assertEquals(usageCount, db.queryForObject("select count(*) from course_visit_usage where course_id=?", Integer.class, courseId));
        assertEquals(movementCount, db.queryForObject("select count(*) from course_ledger where course_id=?", Integer.class, courseId));
    }

    @Test
    void printActorFallsBackToUsernameWithoutRewritingHistoricalActorIdsOrSnapshot() {
        String frozen = reply("GET", "/api/course-usage-prints/" + printId, null).body().path("documentSnapshot").asText();
        Long originalStaff = db.queryForObject("select staff_id from app_user where user_id=?", Long.class, actorId);
        try {
            db.update("update app_user set staff_id=null where user_id=?", actorId);
            var response = reply("GET", "/api/course-usage-prints/" + printId, null);
            assertEquals(200, response.status(), response.body().toString());
            assertEquals("manager", response.body().path("issuedBy").asText());
            assertEquals(actorId.longValue(), response.body().path("issuedById").asLong());
            assertEquals("manager", response.body().path("events").get(0).path("printedBy").asText());
            assertEquals(actorId.longValue(), response.body().path("events").get(0).path("printedById").asLong());
            assertEquals(actorId, db.queryForObject("select issued_by from course_usage_print where print_id=?", Long.class, printId));
            assertEquals(actorId, db.queryForObject("select printed_by from course_usage_print_event where print_id=? and event_type='Issued'", Long.class, printId));
            assertEquals(frozen, response.body().path("documentSnapshot").asText());
        } finally {
            db.update("update app_user set staff_id=? where user_id=?", originalStaff, actorId);
        }
    }

    @Test
    void requiresAuthenticationAndEnforcesSourceBranchForReadsAndCopies() {
        assertEquals(401, replyAs(null, "GET", "/api/course-usage-prints/" + printId, null).status());
        String therapist = login("therapist", "therapist123");
        assertEquals(200, replyAs(therapist, "GET", "/api/course-usage-prints/" + printId, null).status());
        long second = db.queryForObject("select branch_id from branch where branch_code='02'", Long.class);
        db.update("update course set branch_id=? where course_id=?", second, courseId);
        assertEquals(403, replyAs(therapist, "GET", "/api/course-usage-prints/" + printId, null).status());
        assertEquals(403, replyAs(therapist, "POST", "/api/course-usage-prints/" + printId + "/reprints", Map.of()).status());
        assertEquals(403, replyAs(therapist, "POST", "/api/course-usages/" + usageId + "/print-revisions", Map.of()).status());
        assertEquals(404, reply("GET", "/api/course-usage-prints/99999999", null).status());
    }
}
