package com.sabyeguyz.schemacheck;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.*;
import com.sabyeguyz.clinicv2.course.*;
import com.sabyeguyz.clinicv2.enums.*;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** US097.02: real HTTP/DB correction, never rewriting a historical Paid payout. */
class PaidCourseUsageReversalHttpTest extends CoursePaymentHttpTestSupport {
    private long usageId, movementId, allocationId;
    private List<Map<String,Object>> historicalPayments;

    private void fixture(boolean ownerPaid, boolean treatingPaid, boolean sameStaff) {
        db.update("delete from staff_pay_adjustment");
        db.update("delete from staff_monthly_payment");
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
            var course = context.getBean(CourseRepository.class).findById(courseId).orElseThrow();
            Staff owner = course.getOwnerStaff();
            Staff treating = sameStaff ? owner : em.createQuery("select s from Staff s where s.id <> :owner order by s.id", Staff.class)
                    .setParameter("owner", owner.getId()).setMaxResults(1).getSingleResult();
            PatientVisit visit = new PatientVisit();
            visit.setPatient(course.getPurchaserPatient()); visit.setBranch(course.getBranch());
            visit.setAssignedStaff(owner); visit.setTreatingStaff(owner); visit.setVisitStatus(VisitStatus.Completed);
            visit.setVisitDate(Instant.parse("2026-10-02T03:00:00Z"));
            visit.setCheckoutTime(Instant.parse("2026-10-02T04:00:00Z"));
            visit.setVisitCreateAt(Instant.now()); visit.setVisitCreateBy(actorId); em.persist(visit);
            var usage = context.getBean(CourseVisitUsageService.class).use(course, visit, course.getPurchaserPatient(), owner,
                    LocalDate.of(2026,10,2), actorId);
            // Explicit captured source components exercise the independent owner/treating payout contract.
            usage.setSubstituteTreatmentFeeAmount(new BigDecimal("25.00"));
            usage.setOwnerNetCommission(new BigDecimal("50.00"));
            var allocation = context.getBean(CourseCommissionService.class).recordAllocation(usage, new BigDecimal("50.00"), 1, null, actorId);
            allocation.setTreatingStaff(treating);
            var ownerPayment = payment(em, owner, course.getBranch(), "2026-10", ownerPaid, new BigDecimal(sameStaff ? "75.00" : "50.00"));
            var treatingPayment = sameStaff ? ownerPayment : payment(em, treating, course.getBranch(), "2026-10", treatingPaid, new BigDecimal("25.00"));
            allocation.setOwnerMonthlyPayment(ownerPayment); allocation.setTreatingMonthlyPayment(treatingPayment);
            if (ownerPaid) payment(em, owner, course.getBranch(), "2026-12", true, BigDecimal.ONE);
            if (treatingPaid && !sameStaff) payment(em, treating, course.getBranch(), "2026-12", true, BigDecimal.ONE);
            usageId = usage.getId(); allocationId = allocation.getId();
        });
        movementId = db.queryForObject("select ledger_id from course_ledger where visit_usage_id=? and entry_type='Treatment'", Long.class, usageId);
        historicalPayments = db.queryForList("select * from staff_monthly_payment order by salary_payment_id");
    }

    private StaffMonthlyPayment payment(EntityManager em, Staff person, com.sabyeguyz.clinicv2.branch.Branch branch,
                                         String month, boolean paid, BigDecimal amount) {
        StaffMonthlyPayment row = new StaffMonthlyPayment(); row.setStaff(person); row.setBranch(branch); row.setPaymentMonth(month);
        row.setPaymentStatus(paid ? PaymentStatus.Paid : PaymentStatus.Open);
        row.setStatusChangedBy(actorId); row.setStatusChangedAt(Instant.now());
        row.setCourseCommissionAmount(amount); row.setSingleVisitFeeAmount(BigDecimal.ZERO);
        row.setSubstituteFeeAmount(BigDecimal.ZERO); row.setAdjustmentAmount(BigDecimal.ZERO); row.setNetPaymentAmount(amount);
        if (paid) { row.setPaidAt(Instant.now()); row.setPaidDate(LocalDate.of(2026,12,31)); row.setPaymentReference("test-paid-source"); }
        em.persist(row); return row;
    }

    private HttpResult reverse(String reason) {
        return reply("POST", "/api/course-movements/"+movementId+"/reverse",
                Map.of("reasonCode","WrongEntryOrService","reason",reason));
    }

    private void assertCorrection(boolean ownerPaid, boolean treatingPaid, int expectedAdjustments) {
        assertEquals(historicalPayments, db.queryForList("select * from staff_monthly_payment order by salary_payment_id"));
        assertEquals(10, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        assertEquals(0, db.queryForObject("select visits_used from course where course_id=?", Integer.class, courseId));
        assertEquals(10, db.queryForObject("select remaining_visits from course_entitlement where course_id=? and patient_id=?", Integer.class, courseId, patientId));
        assertEquals("Reversed", db.queryForObject("select usage_status::text from course_visit_usage where visit_usage_id=?", String.class, usageId));
        assertEquals(new BigDecimal("50.00"), db.queryForObject("select owner_net_commission from commission_allocation_ledger where allocation_id=?", BigDecimal.class, allocationId));
        assertEquals(new BigDecimal("25.00"), db.queryForObject("select treatment_fee_amount from commission_allocation_ledger where allocation_id=?", BigDecimal.class, allocationId));
        assertEquals(ownerPaid ? new BigDecimal("0.00") : new BigDecimal("-50.00"), db.queryForObject("select owner_net_commission from commission_allocation_ledger where reverses_allocation_id=?", BigDecimal.class, allocationId));
        assertEquals(treatingPaid ? new BigDecimal("0.00") : new BigDecimal("-25.00"), db.queryForObject("select treatment_fee_amount from commission_allocation_ledger where reverses_allocation_id=?", BigDecimal.class, allocationId));
        assertEquals(expectedAdjustments, db.queryForObject("select count(*) from staff_pay_adjustment", Integer.class));
        BigDecimal total = new BigDecimal(ownerPaid ? "-50.00" : "0.00").add(new BigDecimal(treatingPaid ? "-25.00" : "0.00"));
        assertEquals(total, db.queryForObject("select coalesce(sum(amount),0)::numeric(12,2) from staff_pay_adjustment", BigDecimal.class));
        assertEquals(expectedAdjustments, db.queryForObject("select count(*) from staff_pay_adjustment where allocation_id=? and course_id=? and original_month='2026-10' and applied_month='2027-01' and adjustment_status='Pending' and processed_amount=0 and remaining_amount=abs(amount) and approved_by is null and approved_at is null and visit_id is not null", Integer.class, allocationId, courseId));
        assertEquals(0, db.queryForObject("select count(*) from adjustment_application", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from course_ledger where reverses_ledger_id=? and entry_type='Reversal'", Integer.class, movementId));
    }

    @ParameterizedTest @CsvSource({"true,false,1","false,true,1","true,true,2","false,false,0"})
    void independentPaidComponentsUsePendingAdjustmentsOnly(boolean ownerPaid, boolean treatingPaid, int count) {
        fixture(ownerPaid,treatingPaid,false);
        var result=reverse("Wrong usage entry"); assertEquals(200,result.status(),result.body().toString());
        assertCorrection(ownerPaid,treatingPaid,count);
        assertEquals(400,reverse("Duplicate correction").status());
        assertCorrection(ownerPaid,treatingPaid,count);
    }

    @Test void combinesSameStaffComponentsWithoutLosingSourceOrApprovingDeduction() {
        fixture(true,true,true); assertEquals(200,reverse("Incorrect combined usage").status()); assertCorrection(true,true,1);
    }

    @Test void replacementLinksToReversedUsageAndReusesItsSequenceWithoutRewritingPaidHistory() {
        fixture(true,true,false); assertEquals(200,reverse("Incorrect usage replaced by actual treatment").status());
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var course = context.getBean(CourseRepository.class).findById(courseId).orElseThrow();
            var original = context.getBean(CourseVisitUsageRepository.class).findById(usageId).orElseThrow();
            var replacement = context.getBean(CourseVisitUsageService.class).use(course,original.getVisit(),course.getPurchaserPatient(),
                    course.getOwnerStaff(),LocalDate.of(2026,10,2),actorId);
            assertEquals(original.getUsageSequence(),replacement.getUsageSequence());
            assertEquals(usageId,replacement.getReplacesUsage().getId());
            assertNotEquals(usageId,replacement.getId());
        });
        assertEquals(historicalPayments,db.queryForList("select * from staff_monthly_payment order by salary_payment_id"));
        assertEquals(2,db.queryForObject("select count(*) from staff_pay_adjustment",Integer.class));
        assertEquals(9,db.queryForObject("select remaining_visits from course where course_id=?",Integer.class,courseId));
    }

    @Test void invalidReasonDoesNotRestoreRightsOrCreateAnyCorrection() {
        fixture(true,true,false); assertEquals(400,reverse(" ").status());
        assertEquals(9,db.queryForObject("select remaining_visits from course where course_id=?",Integer.class,courseId));
        assertEquals(0,db.queryForObject("select count(*) from staff_pay_adjustment",Integer.class));
        assertEquals(0,db.queryForObject("select count(*) from commission_allocation_ledger where reverses_allocation_id=?",Integer.class,allocationId));
        assertEquals(historicalPayments,db.queryForList("select * from staff_monthly_payment order by salary_payment_id"));
    }

    @Test void concurrentRequestsRestoreRightsAndProposeEachCorrectionOnce() throws Exception {
        fixture(true,true,false);
        ExecutorService executor=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> request=()->{start.await();return reverse("Concurrent duplicate correction").status();};
            var first=executor.submit(request); var second=executor.submit(request); start.countDown();
            var statuses=new ArrayList<>(List.of(first.get(40,TimeUnit.SECONDS),second.get(40,TimeUnit.SECONDS))); Collections.sort(statuses);
            assertEquals(List.of(200,400),statuses); assertCorrection(true,true,2);
        } finally { executor.shutdownNow(); }
    }

    @Test void failedAdjustmentPersistenceRollsBackCancellationRightsAndBothComponents() {
        fixture(true,true,false);
        db.execute("alter table staff_pay_adjustment add constraint test_reject_usage_adjustment check (amount > 0)");
        try {
            assertTrue(reverse("Force persistence rollback").status() >= 400);
            assertEquals(9,db.queryForObject("select remaining_visits from course where course_id=?",Integer.class,courseId));
            assertEquals("Completed",db.queryForObject("select usage_status::text from course_visit_usage where visit_usage_id=?",String.class,usageId));
            assertEquals(0,db.queryForObject("select count(*) from staff_pay_adjustment",Integer.class));
            assertEquals(0,db.queryForObject("select count(*) from commission_allocation_ledger where reverses_allocation_id=?",Integer.class,allocationId));
            assertEquals(0,db.queryForObject("select count(*) from transaction_cancellation where visit_usage_id=?",Integer.class,usageId));
            assertEquals(0,db.queryForObject("select count(*) from course_ledger where reverses_ledger_id=? and entry_type='Reversal'",Integer.class,movementId));
            assertEquals(historicalPayments,db.queryForList("select * from staff_monthly_payment order by salary_payment_id"));
        } finally { db.execute("alter table staff_pay_adjustment drop constraint test_reject_usage_adjustment"); }
    }
}
