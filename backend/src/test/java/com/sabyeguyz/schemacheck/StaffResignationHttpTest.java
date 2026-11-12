package com.sabyeguyz.schemacheck;

import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CourseCommissionService;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** US-105/106: resignation is retained and the shared post-termination commission rule applies. */
class StaffResignationHttpTest extends CoursePaymentHttpTestSupport {
    @Test
    void terminationValidatesDateDisablesLinkedAccountAndKeepsStaffHistory() {
        long staffId = db.queryForObject("select staff_id from staff where staff_code='THP-001'", Long.class);
        LocalDate hireDate = db.queryForObject("select hire_date from staff where staff_id=?", LocalDate.class, staffId);
        int staffCount = db.queryForObject("select count(*) from staff", Integer.class);
        Integer linkedAccounts = db.queryForObject("select count(*) from app_user where staff_id=?", Integer.class, staffId);

        var invalid = reply("POST", "/api/staff/" + staffId + "/terminate",
                Map.of("terminationDate", hireDate.minusDays(1).toString()));
        assertEquals(400, invalid.status(), invalid.body().toString());
        assertEquals(hireDate, db.queryForObject("select hire_date from staff where staff_id=?", LocalDate.class, staffId));
        assertEquals(1, db.queryForObject("select count(*) from staff where staff_id=? and staff_status=true and termination_date is null",
                Integer.class, staffId));

        var terminated = reply("POST", "/api/staff/" + staffId + "/terminate",
                Map.of("terminationDate", hireDate.toString()));
        assertEquals(200, terminated.status(), terminated.body().toString());
        assertFalse(terminated.body().path("active").asBoolean());
        assertEquals(hireDate, db.queryForObject("select termination_date from staff where staff_id=?", LocalDate.class, staffId));
        assertEquals(staffCount, db.queryForObject("select count(*) from staff", Integer.class));
        assertEquals(linkedAccounts.intValue(), db.queryForObject(
                "select count(*) from app_user where staff_id=? and user_status=false", Integer.class, staffId));
        if (linkedAccounts > 0) {
            assertNotEquals(200, replyAs(null, "POST", "/api/auth/login",
                    Map.of("username", "therapist", "password", "therapist123")).status());
        }
    }

    @Test
    void commonOwnerRuleStopsCommissionOnlyAfterTerminationDate() {
        long staffId = db.queryForObject("select staff_id from staff where staff_code='THP-001'", Long.class);
        LocalDate terminationDate = db.queryForObject("select hire_date from staff where staff_id=?", LocalDate.class, staffId);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var staffService = context.getBean(com.sabyeguyz.clinicv2.staff.StaffService.class);
            staffService.terminate(staffId, terminationDate, actorId);
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            Staff owner = em.find(Staff.class, staffId);
            var course = context.getBean(CourseRepository.class).findById(courseId).orElseThrow();
            assertTrue(CourseCommissionService.ownerEarns(course, owner, terminationDate));
            assertFalse(CourseCommissionService.ownerEarns(course, owner, terminationDate.plusDays(1)));
            assertEquals(java.math.BigDecimal.ZERO,
                    CourseCommissionService.ownerNetOf(course, 1, owner, terminationDate.plusDays(1)));
        });
    }
}
