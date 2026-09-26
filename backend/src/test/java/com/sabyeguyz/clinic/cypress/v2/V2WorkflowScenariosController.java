package com.sabyeguyz.clinic.cypress.v2;

import com.sabyeguyz.clinic.cypress.V2RepositoryStub;
import com.sabyeguyz.clinicv2.appointment.*;
import com.sabyeguyz.clinicv2.audit.*;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.commission.*;
import com.sabyeguyz.clinicv2.course.*;
import com.sabyeguyz.clinicv2.enums.*;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Test-only observations of production services; assertions belong to Cypress. */
@RestController
@Profile("cypress")
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/__cypress/v2/workflows")
public class V2WorkflowScenariosController {
    @GetMapping("/appointment/{scenario}")
    public Map<String, Object> appointment(@PathVariable String scenario) {
        var appointments = new V2RepositoryStub<>(AppointmentRepository.class);
        var history = new V2RepositoryStub<>(AppointmentStatusHistoryRepository.class);
        var service = new AppointmentService(appointments.proxy(), history.proxy());
        Appointment existing = new Appointment();
        existing.setStaff(new Staff());
        existing.setAppointmentStatus(scenario.equals("completed") ? AppointmentStatus.Completed : AppointmentStatus.Confirmed);
        appointments.on("findById", args -> Optional.of(existing));
        appointments.on("existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn",
                args -> scenario.equals("conflict"));
        return V2RepositoryStub.outcome(() -> {
            Appointment result = switch (scenario) {
                case "book", "conflict" -> service.book(new Patient(), new Branch(), new Staff(), new Service(), null,
                        LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), 1L);
                case "cancel", "completed" -> service.cancel(1L, "ผู้ป่วยขอยกเลิก", 9L);
                case "reschedule" -> service.reschedule(1L, LocalDate.of(2026, 12, 5), LocalTime.of(10, 0), "ผู้ป่วยขอเลื่อน", 9L);
                default -> throw new IllegalArgumentException("Unknown scenario");
            };
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("status", result.getAppointmentStatus());
            resultMap.put("date", result.getAppointmentDate());
            resultMap.put("time", result.getAppointmentTime());
            resultMap.put("historySaves", history.calls("save"));
            return resultMap;
        });
    }

    @GetMapping("/visit/{scenario}")
    public Map<String, Object> visit(@PathVariable String scenario) {
        var visits = new V2RepositoryStub<>(PatientVisitRepository.class);
        Appointment appointment = new Appointment();
        appointment.setPatient(new Patient());
        appointment.setBranch(new Branch());
        appointment.setStaff(new Staff());
        appointment.setAppointmentStatus(AppointmentStatus.Arrived);
        List<List<Long>> arrivedCalls = new ArrayList<>();
        List<List<Long>> completedCalls = new ArrayList<>();
        // Explicit recording collaborator replaces the original class mock.
        AppointmentService appointments = new AppointmentService(null, null) {
            @Override public Appointment markArrived(Long id, Long actor) {
                arrivedCalls.add(Arrays.asList(id, actor));
                return appointment;
            }
            @Override public Appointment markCompleted(Long id, Long actor) {
                completedCalls.add(Arrays.asList(id, actor));
                return appointment;
            }
        };
        PatientVisit existing = new PatientVisit();
        existing.setAppointment(appointment);
        existing.setVisitStatus(scenario.equals("cancelled") ? VisitStatus.Cancelled : VisitStatus.InService);
        visits.on("findById", args -> Optional.of(existing));
        var service = new PatientVisitService(visits.proxy(), appointments);
        return V2RepositoryStub.outcome(() -> {
            PatientVisit result = switch (scenario) {
                case "check-in" -> service.checkInFromAppointment(5L, 1L);
                case "missing-patient" -> service.checkInWalkIn(null, new Branch(), null, 1L);
                case "complete" -> service.complete(9L, 1L);
                case "cancelled" -> service.startService(9L, 1L);
                default -> throw new IllegalArgumentException("Unknown scenario");
            };
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("status", result.getVisitStatus());
            resultMap.put("sameAppointment", result.getAppointment() == appointment);
            resultMap.put("checkoutTime", result.getCheckoutTime());
            resultMap.put("arrivedCalls", arrivedCalls);
            resultMap.put("completedCalls", completedCalls);
            return resultMap;
        });
    }

    @GetMapping("/audit/{scenario}")
    public Map<String, Object> audit(@PathVariable String scenario) {
        var repository = new V2RepositoryStub<>(AuditLogRepository.class);
        return V2RepositoryStub.outcome(() -> {
            var log = new AuditLogService(repository.proxy()).record("branch", "1",
                    scenario.equals("missing-action") ? null : AuditAction.UPDATE,
                    "{\"phone\":\"080\"}", "{\"phone\":\"081\"}", 1L);
            return Map.of("tableName", log.getTableName(), "action", log.getAction());
        });
    }

    @GetMapping("/transfer/{scenario}")
    public Map<String, Object> transfer(@PathVariable String scenario) {
        var transfers = new V2RepositoryStub<>(CourseTransferRepository.class);
        var ledger = new V2RepositoryStub<>(CourseLedgerRepository.class);
        var members = new V2RepositoryStub<>(CourseSharedMemberRepository.class);
        Course course = new Course(); setId(course, 1L);
        Patient from = new Patient(); setId(from, 10L);
        Patient to = new Patient(); setId(to, 20L);
        ledger.on("sumQtyChangeByCourseAndPatient", args -> Long.valueOf(10).equals(args[1])
                ? (scenario.equals("insufficient") ? 2L : 6L) : 0L);
        transfers.on("save", args -> { setId(args[0], 100L); return args[0]; });
        return V2RepositoryStub.outcome(() -> {
            var result = new CourseTransferService(transfers.proxy(), ledger.proxy(), members.proxy())
                    .transfer(course, from, scenario.equals("self") ? from : to,
                            scenario.equals("self") ? 1 : 4, "ย้ายให้ครอบครัว", 1L);
            return Map.of("qtyTransferred", result.getQtyTransferred(), "ledgerSaves", ledger.calls("save"),
                    "memberSaves", members.calls("save"));
        });
    }

    @GetMapping("/usage/{scenario}")
    public Map<String, Object> usage(@PathVariable String scenario) {
        var usages = new V2RepositoryStub<>(CourseVisitUsageRepository.class);
        var ledger = new V2RepositoryStub<>(CourseLedgerRepository.class);
        var courses = new V2RepositoryStub<>(CourseRepository.class);
        var allocations = new V2RepositoryStub<>(CommissionAllocationLedgerRepository.class);
        var rules = new V2RepositoryStub<>(TreatmentFeeRuleRepository.class);
        Course course = lockedCourse();
        Staff owner = new Staff(); setId(owner, 10L);
        Staff substitute = new Staff(); setId(substitute, 11L);
        Patient patient = new Patient(); setId(patient, 100L);
        course.setCaseOwnerStaff(owner);
        ledger.on("sumQtyChangeByCourseAndPatient", args -> scenario.equals("no-balance") ? 0L : 10L);
        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setFeeType(scenario.equals("percentage") ? FeeType.Percentage : FeeType.Fixed);
        rule.setFeeValue(new BigDecimal(scenario.equals("percentage") ? "0.03" : scenario.equals("capped") ? "60" : "45"));
        rule.setPercentageBase(CalculationBase.CourseValuePerVisit);
        rules.on("findActiveOn", args -> List.of(rule));
        if (scenario.equals("capped")) course.setCommissionAllocationPerVisit(new BigDecimal("40.00"));
        if (scenario.equals("expired")) course.setExpiryDate(LocalDate.of(2020, 1, 1));
        if (scenario.equals("exhausted")) course.setRemainingVisits(0);
        return V2RepositoryStub.outcome(() -> {
            var service = new CourseVisitUsageService(usages.proxy(), ledger.proxy(), courses.proxy(),
                    allocations.proxy(), new TreatmentFeeRuleResolver(rules.proxy()));
            var result = service.use(course, null, patient, scenario.equals("owner") ? owner : substitute,
                    LocalDate.of(2026, 9, 1), 1L);
            return Map.of("gross", result.getGrossCommissionAllocation(), "fee", result.getSubstituteTreatmentFeeAmount(),
                    "ownerNet", result.getOwnerNetCommission(), "remainingVisits", course.getRemainingVisits(),
                    "visitsUsed", course.getVisitsUsed());
        });
    }

    private static Course lockedCourse() {
        Course course = new Course(); setId(course, 1L);
        course.setCoursePrice(new BigDecimal("10000.00"));
        course.setTotalVisits(10); course.setRemainingVisits(10); course.setVisitsUsed(0);
        course.setExpiryDate(LocalDate.of(2026, 12, 31)); course.setCourseStatus(CourseStatus.Active);
        course.setCommissionAllocationPerVisit(new BigDecimal("70.00"));
        course.setTotalCourseCommissionPool(new BigDecimal("700.00"));
        course.setGrossCommissionAllocatedTotal(BigDecimal.ZERO);
        course.setOwnerNetCommissionReleasedTotal(BigDecimal.ZERO);
        course.setSubstituteTreatmentFeeTotal(BigDecimal.ZERO);
        course.setCommissionOutstanding(new BigDecimal("700.00"));
        return course;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true); field.set(entity, id);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
