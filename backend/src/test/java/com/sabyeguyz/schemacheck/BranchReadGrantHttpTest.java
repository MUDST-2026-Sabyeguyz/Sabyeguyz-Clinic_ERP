package com.sabyeguyz.schemacheck;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.auth.AppUserService;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.course.CourseVisitUsageService;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** AC004.05/.06 and041.04: explicit read grants do not authorize foreign-branch writes. */
class BranchReadGrantHttpTest extends CoursePaymentHttpTestSupport {
    private long foreignCourse, movement, sale, otherBranch, therapistUser, foreignAppointment, foreignTherapist;
    private String therapist;

    @BeforeEach void foreignBranchUsage() {
        otherBranch = db.queryForObject("select branch_id from branch where branch_code='02'", Long.class);
        therapistUser = db.queryForObject("select user_id from app_user where username='therapist'", Long.class);
        long seller = db.queryForObject("select staff_id from staff where staff_code='THP-002'", Long.class);
        foreignTherapist = seller;
        long template = db.queryForObject("select service_id from service where service_code='COURSE_10'", Long.class);
        var purchase = reply("POST", "/api/course-purchases", Map.of("patientId", patientId,
                "branchCode", "02", "courseId", template, "caseOwnerStaffId", seller, "paymentLabelCode", "FULL"));
        assertEquals(201, purchase.status(), purchase.body().toString());
        foreignCourse = purchase.body().path("id").asLong();
        sale = db.queryForObject("select sale_transaction_id from course where course_id=?", Long.class, foreignCourse);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var course = context.getBean(CourseRepository.class).findById(foreignCourse).orElseThrow();
            var visit = new PatientVisit();
            visit.setPatient(course.getPurchaserPatient()); visit.setBranch(course.getBranch());
            visit.setAssignedStaff(course.getSellerStaff()); visit.setTreatingStaff(course.getSellerStaff());
            visit.setVisitStatus(VisitStatus.Completed); visit.setVisitDate(Instant.parse("2026-10-02T03:00:00Z"));
            visit.setCheckoutTime(Instant.parse("2026-10-02T04:00:00Z"));
            visit.setVisitCreateAt(Instant.now()); visit.setVisitCreateBy(actorId);
            EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class)).persist(visit);
            var usage = context.getBean(CourseVisitUsageService.class).use(course, visit,
                    course.getPurchaserPatient(), course.getSellerStaff(), LocalDate.of(2026, 10, 2), actorId);
            movement = db.queryForObject("select ledger_id from course_ledger where visit_usage_id=? and entry_type='Treatment'", Long.class, usage.getId());
        });
        db.update("update branch set opening_time='09:00',closing_time='18:00',default_appointment_minutes=30 where branch_id=?", otherBranch);
        var booking = reply("POST", "/api/appointments", Map.of("branchCode", "02", "patientId", patientId,
                "therapistId", seller, "serviceId", template, "patientType", "CURRENT",
                "startAt", "2040-02-06T10:00:00+07:00", "endAt", "2040-02-06T10:30:00+07:00"));
        assertEquals(201, booking.status(), booking.body().toString());
        foreignAppointment = booking.body().path("id").asLong();
        context.getBean(AppUserService.class).revokeBranchAccess(therapistUser, otherBranch);
        therapist = login("therapist", "therapist123");
    }

    @Test void explicitGrantAllowsFilteredForeignUsageReadButHidesOtherStaffPayAndPreservesHomeDefault() {
        assertEquals(403, replyAs(therapist, "GET", "/api/course-usages?branchCode=02", null).status());
        assertEquals(403, replyAs(therapist, "GET", "/api/receipts/" + sale, null).status());
        assertEquals(403, replyAs(therapist, "GET", "/api/course-purchases/" + foreignCourse, null).status());
        context.getBean(AppUserService.class).grantBranchAccess(therapistUser, otherBranch);
        int remaining = db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, foreignCourse);
        long ledgerRows = db.queryForObject("select count(*) from course_ledger", Long.class);
        var read = replyAs(therapist, "GET", "/api/course-usages?branchCode=02&from=2026-10-02&to=2026-10-02", null);
        assertEquals(200, read.status(), read.body().toString()); assertEquals(1, read.body().size());
        var row = read.body().get(0);
        assertEquals(foreignCourse, row.path("coursePurchaseId").asLong()); assertEquals("02", row.path("branchCode").asText());
        assertFalse(row.path("usingPatientHn").asText().isBlank());
        for (String field : new String[]{"commissionAllocationPerVisit", "treatmentFeeAmount", "ownerNetCommission"}) {
            assertTrue(row.has(field), field); assertTrue(row.path(field).isNull(), field);
        }
        var saleRead = replyAs(therapist, "GET", "/api/payments?branchCode=02", null);
        assertEquals(200, saleRead.status(), saleRead.body().toString());
        assertEquals(1, saleRead.body().path("items").size());
        assertEquals(sale, saleRead.body().path("items").get(0).path("id").asLong());
        assertEquals(200, replyAs(therapist, "GET", "/api/receipts/" + sale, null).status());
        var detail = replyAs(therapist, "GET", "/api/course-purchases/" + foreignCourse, null);
        assertEquals(200, detail.status(), detail.body().toString());
        for (String field : new String[]{"lockedCommissionRate", "totalCourseCommissionPool", "commissionAllocationPerVisit"}) {
            assertTrue(detail.body().path("purchase").path(field).isNull(), field);
        }
        assertFalse(detail.body().path("allocations").isEmpty());
        for (var allocation : detail.body().path("allocations")) {
            assertTrue(allocation.path("gross").isNull());
            assertTrue(allocation.path("fee").isNull());
            assertTrue(allocation.path("ownerNet").isNull());
        }
        var home = replyAs(therapist, "GET", "/api/course-usages", null);
        assertEquals(200, home.status()); assertEquals(0, home.body().size());
        assertEquals(remaining, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, foreignCourse));
        assertEquals(ledgerRows, db.queryForObject("select count(*) from course_ledger", Long.class));
        context.getBean(AppUserService.class).revokeBranchAccess(therapistUser, otherBranch);
        assertEquals(403, replyAs(therapist, "GET", "/api/course-usages?branchCode=02", null).status());
        assertEquals(403, replyAs(therapist, "GET", "/api/receipts/" + sale, null).status());
        assertEquals(403, replyAs(therapist, "GET", "/api/course-purchases/" + foreignCourse, null).status());
    }

    @Test void readGrantNeverAllowsReverseCancelVoidOrForeignBranchCreation() {
        context.getBean(AppUserService.class).grantBranchAccess(therapistUser, otherBranch);
        var before = db.queryForMap("select course_status::text,remaining_visits,visits_used from course where course_id=?", foreignCourse);
        long cancellations = db.queryForObject("select count(*) from transaction_cancellation", Long.class);
        long ledgerRows = db.queryForObject("select count(*) from course_ledger", Long.class);
        var reason = Map.of("reasonCode", "WrongEntryOrService", "reason", "Foreign branch must remain read only");
        long visits = db.queryForObject("select count(*) from patient_visit", Long.class);
        assertEquals(403, replyAs(therapist, "POST", "/api/course-usages", Map.of(
                "coursePurchaseId", foreignCourse, "appointmentId", foreignAppointment,
                "patientId", patientId, "treatingStaffId", foreignTherapist)).status());
        assertEquals(visits, db.queryForObject("select count(*) from patient_visit", Long.class));
        assertEquals(403, replyAs(therapist, "POST", "/api/course-movements/" + movement + "/reverse", reason).status());
        assertEquals(403, replyAs(therapist, "POST", "/api/course-purchases/" + foreignCourse + "/cancel", reason).status());
        assertEquals(403, replyAs(therapist, "POST", "/api/payments/" + sale + "/reverse", reason).status());
        long template = db.queryForObject("select service_id from service where service_code='COURSE_10'", Long.class);
        assertEquals(403, replyAs(therapist, "POST", "/api/course-purchases", Map.of("patientId", patientId,
                "branchCode", "02", "courseId", template, "paymentLabelCode", "FULL")).status());
        assertEquals(before, db.queryForMap("select course_status::text,remaining_visits,visits_used from course where course_id=?", foreignCourse));
        assertEquals(cancellations, db.queryForObject("select count(*) from transaction_cancellation", Long.class));
        assertEquals(ledgerRows, db.queryForObject("select count(*) from course_ledger", Long.class));
        assertTrue(db.queryForObject("select transaction_status from sales_transaction where transaction_id=?", Boolean.class, sale));
    }

    @Test void closedForeignBranchStillRejectsReadDespiteGrant() {
        context.getBean(AppUserService.class).grantBranchAccess(therapistUser, otherBranch);
        db.update("update branch set branch_status=false where branch_id=?", otherBranch);
        assertEquals(403, replyAs(therapist, "GET", "/api/course-usages?branchCode=02", null).status());
    }
}
