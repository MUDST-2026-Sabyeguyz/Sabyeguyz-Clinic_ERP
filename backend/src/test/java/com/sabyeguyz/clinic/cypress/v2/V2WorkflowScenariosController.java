package com.sabyeguyz.clinic.cypress.v2;

import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedger;
import com.sabyeguyz.clinicv2.course.CourseEntitlement;
import com.sabyeguyz.clinicv2.course.CourseEntitlementRepository;
import com.sabyeguyz.clinicv2.course.CourseLedger;
import com.sabyeguyz.clinicv2.course.CourseTransfer;
import com.sabyeguyz.clinicv2.course.CourseUsagePrint;
import com.sabyeguyz.clinicv2.course.CourseUsagePrintEvent;
import com.sabyeguyz.clinicv2.course.CourseUsagePrintEventRepository;
import com.sabyeguyz.clinicv2.course.CourseUsagePrintRepository;
import com.sabyeguyz.clinicv2.course.CourseUsagePrintService;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.TransferStatus;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.sale.TransactionCancellation;
import com.sabyeguyz.clinicv2.sale.TransactionCancellationRepository;
import com.sabyeguyz.clinicv2.sale.TransactionCancellationService;

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
        existing.setAppointmentStatus(switch (scenario) {
            case "completed" -> AppointmentStatus.Completed;
            case "confirm", "arrive-unconfirmed" -> AppointmentStatus.Unconfirmed;
            default -> AppointmentStatus.Confirmed;
        });
        if (scenario.equals("link-patient") || scenario.equals("arrive-new-patient")) {
            existing.setAppointmentPatientType(com.sabyeguyz.clinicv2.enums.AppointmentPatientType.NEW);
        }
        if (scenario.equals("link-patient-already-linked") || scenario.equals("arrive-confirmed")) {
            existing.setPatient(new Patient());
        }
        appointments.on("findById", args -> Optional.of(existing));
        appointments.on("findByIdForUpdate", args -> Optional.of(existing));
        appointments.on("existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn",
                args -> scenario.equals("conflict"));
        return V2RepositoryStub.outcome(() -> {
            Appointment result = switch (scenario) {
                case "book", "conflict" -> service.book(new Patient(), new Branch(), new Staff(), new Service(), null,
                        LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), 1L);
                case "cancel", "completed" -> service.cancel(1L, "ผู้ป่วยขอยกเลิก", 9L);
                case "reschedule" -> service.reschedule(1L, LocalDate.of(2026, 12, 5), LocalTime.of(10, 0), "ผู้ป่วยขอเลื่อน", 9L);
                case "book-unconfirmed" -> service.book(new Patient(), new Branch(), new Staff(), new Service(), null,
                        LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), AppointmentStatus.Unconfirmed, 1L);
                case "book-bad-initial-status" -> service.book(new Patient(), new Branch(), new Staff(), new Service(), null,
                        LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), AppointmentStatus.Completed, 1L);
                case "book-new-patient" -> service.bookNewPatient("สมชาย ใจดี", "0812345678", new Branch(), new Staff(),
                        com.sabyeguyz.clinicv2.enums.RequestedServiceType.Course, null, LocalDate.of(2026, 12, 1),
                        LocalTime.of(9, 0), AppointmentStatus.Unconfirmed, 1L);
                case "new-nickname-with-phone" -> service.bookNewPatient("แบงค์", "0812345678", new Branch(), new Staff(),
                        null, null, LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), null, 1L);
                case "new-nickname-without-phone" -> service.bookNewPatient("แบงค์", " ", new Branch(), new Staff(),
                        null, null, LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), null, 1L);
                case "new-without-name" -> service.bookNewPatient("  ", "0812345678", new Branch(), new Staff(),
                        null, null, LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), null, 1L);
                case "confirm", "confirm-already-confirmed" -> service.confirm(1L, 9L);
                case "link-patient", "link-patient-already-linked" -> service.linkRegisteredPatient(1L, new Patient(), 9L);
                case "arrive-unconfirmed", "arrive-new-patient", "arrive-confirmed" -> service.markArrived(1L, 9L);
                default -> throw new IllegalArgumentException("Unknown scenario");
            };
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("status", result.getAppointmentStatus());
            resultMap.put("date", result.getAppointmentDate());
            resultMap.put("time", result.getAppointmentTime());
            resultMap.put("historySaves", history.calls("save"));
            resultMap.put("patientType", result.getAppointmentPatientType());
            resultMap.put("hasPatient", result.getPatient() != null);
            resultMap.put("contactName", result.getContactName());
            resultMap.put("contactPhone", result.getContactPhone());
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
        var entitlements = new V2RepositoryStub<>(CourseEntitlementRepository.class);
        var cancellationRepo = new V2RepositoryStub<>(TransactionCancellationRepository.class);
        var cancellations = new TransactionCancellationService(cancellationRepo.proxy(), null, null, null, null, null);
        Course course = new Course(); setId(course, 1L);
        Patient from = new Patient(); setId(from, 10L);
        Patient to = new Patient(); setId(to, 20L);
        var service = new CourseTransferService(transfers.proxy(), ledger.proxy(), members.proxy(),
                entitlements.proxy(), cancellations);
        if (scenario.startsWith("reverse")) {
            return reverseTransfer(scenario, service, ledger, entitlements, cancellationRepo, course, from, to);
        }
        ledger.on("sumQtyChangeByCourseAndPatient", args -> Long.valueOf(10).equals(args[1])
                ? (scenario.equals("insufficient") ? 2L : 6L) : 0L);
        transfers.on("save", args -> { setId(args[0], 100L); return args[0]; });
        return V2RepositoryStub.outcome(() -> {
            var result = service.transfer(course, from, scenario.equals("self") ? from : to,
                    scenario.equals("self") ? 1 : 4, "ย้ายให้ครอบครัว", 1L);
            var entries = ledger.arguments("save").stream().map(a -> (CourseLedger) a[0]).toList();
            var saved = entitlements.arguments("save").stream().map(a -> (CourseEntitlement) a[0]).toList();
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("qtyTransferred", result.getQtyTransferred());
            resultMap.put("ledgerSaves", ledger.calls("save"));
            resultMap.put("memberSaves", members.calls("save"));
            resultMap.put("status", result.getTransferStatus());
            resultMap.put("ledgerTypes", entries.stream().map(e -> e.getEntryType().name()).toList());
            resultMap.put("ledgerLinked", entries.stream().allMatch(e -> e.getTransfer() == result));
            resultMap.put("entitlementSaves", saved.size());
            resultMap.put("fromAllocated", saved.get(0).getAllocatedVisits());
            resultMap.put("toAllocated", saved.get(1).getAllocatedVisits());
            return resultMap;
        });
    }

    private Map<String, Object> reverseTransfer(String scenario, CourseTransferService service,
                                                 V2RepositoryStub<CourseLedgerRepository> ledger,
                                                 V2RepositoryStub<CourseEntitlementRepository> entitlements,
                                                 V2RepositoryStub<TransactionCancellationRepository> cancellationRepo,
                                                 Course course, Patient from, Patient to) {
        var transfer = new CourseTransfer(); setId(transfer, 100L);
        transfer.setCourse(course); transfer.setFromPatient(from); transfer.setToPatient(to); transfer.setQtyTransferred(4);
        transfer.setTransferStatus(scenario.equals("reverse-twice") ? TransferStatus.Reversed : TransferStatus.Completed);
        var sent = ledgerEntry(CourseLedgerEntryType.TransferOut, from, -4, course);
        var received = ledgerEntry(CourseLedgerEntryType.TransferIn, to, 4, course);
        ledger.on("findByTransferId", args -> List.of(sent, received));
        ledger.on("sumQtyChangeByCourseAndPatient", args -> Long.valueOf(10).equals(args[1]) ? 2L
                : scenario.equals("reverse-receiver-used") ? 1L : 4L);
        String reason = scenario.equals("reverse-no-reason") ? " " : "โอนผิดคน";
        return V2RepositoryStub.outcome(() -> {
            var result = service.reverse(transfer, new Permission(), reason, 9L);
            var entries = ledger.arguments("save").stream().map(a -> (CourseLedger) a[0]).toList();
            var cancellation = (TransactionCancellation) cancellationRepo.arguments("save").get(0)[0];
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("status", result.getTransferStatus());
            resultMap.put("ledgerTypes", entries.stream().map(e -> e.getEntryType().name()).toList());
            resultMap.put("qtyChanges", entries.stream().map(CourseLedger::getQtyChange).toList());
            resultMap.put("balancesAfter", entries.stream().map(CourseLedger::getBalanceAfter).toList());
            resultMap.put("reversalsLinked", entries.get(0).getReversesLedger() == sent && entries.get(1).getReversesLedger() == received);
            resultMap.put("cancellationType", cancellation.getCancellationType());
            resultMap.put("cancellationTargetsTransfer", cancellation.getTransfer() == transfer);
            resultMap.put("entitlementSaves", entitlements.calls("save"));
            return resultMap;
        });
    }

    private static CourseLedger ledgerEntry(CourseLedgerEntryType type, Patient patient, int qty, Course course) {
        var entry = new CourseLedger();
        entry.setEntryType(type); entry.setPatient(patient); entry.setQtyChange(qty); entry.setCourse(course);
        return entry;
    }

    @GetMapping("/usage/{scenario}")
    public Map<String, Object> usage(@PathVariable String scenario) {
        var usages = new V2RepositoryStub<>(CourseVisitUsageRepository.class);
        var ledger = new V2RepositoryStub<>(CourseLedgerRepository.class);
        var courses = new V2RepositoryStub<>(CourseRepository.class);
        var allocations = new V2RepositoryStub<>(CommissionAllocationLedgerRepository.class);
        var rules = new V2RepositoryStub<>(TreatmentFeeRuleRepository.class);
        var entitlements = new V2RepositoryStub<>(CourseEntitlementRepository.class);
        var cancellationRepo = new V2RepositoryStub<>(TransactionCancellationRepository.class);
        var cancellations = new TransactionCancellationService(cancellationRepo.proxy(), null, null, null, null, null);
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
        ledger.on("findByVisitUsageId", args -> ledger.arguments("save").stream().map(a -> (CourseLedger) a[0])
                .filter(l -> l.getVisitUsage() != null).toList());
        allocations.on("findByVisitUsageIdAndEntryType",
                args -> Optional.of((CommissionAllocationLedger) allocations.arguments("save").get(0)[0]));
        entitlements.on("findById", args -> entitlements.arguments("save").isEmpty() ? Optional.empty()
                : Optional.of((CourseEntitlement) entitlements.arguments("save").get(entitlements.arguments("save").size() - 1)[0]));
        boolean ownerTreats = scenario.equals("owner") || scenario.equals("reverse") || scenario.equals("reverse-twice")
                || scenario.equals("reverse-no-reason");
        return V2RepositoryStub.outcome(() -> {
            var service = new CourseVisitUsageService(usages.proxy(), ledger.proxy(), courses.proxy(),
                    allocations.proxy(), new TreatmentFeeRuleResolver(rules.proxy()), entitlements.proxy(), cancellations);
            var result = service.use(course, new PatientVisit(), patient, ownerTreats ? owner : substitute,
                    LocalDate.of(2026, 9, 1), 1L);
            Map<String, Object> resultMap = new LinkedHashMap<>();
            if (!scenario.startsWith("reverse")) {
                var firstEntry = (CourseLedger) ledger.arguments("save").get(0)[0];
                var firstAllocation = (CommissionAllocationLedger) allocations.arguments("save").get(0)[0];
                var firstEntitlement = (CourseEntitlement) entitlements.arguments("save").get(0)[0];
                resultMap.put("gross", result.getGrossCommissionAllocation());
                resultMap.put("fee", result.getSubstituteTreatmentFeeAmount());
                resultMap.put("ownerNet", result.getOwnerNetCommission());
                resultMap.put("remainingVisits", course.getRemainingVisits());
                resultMap.put("visitsUsed", course.getVisitsUsed());
                resultMap.put("usageStatus", result.getUsageStatus());
                resultMap.put("printRequired", result.isPrintRequired());
                resultMap.put("allocationType", firstAllocation.getEntryType());
                resultMap.put("ledgerLinkedToUsage", firstEntry.getVisitUsage() == result);
                resultMap.put("entitlementUsed", firstEntitlement.getUsedVisits());
                resultMap.put("entitlementRemaining", firstEntitlement.getRemainingVisits());
                return resultMap;
            }
            String reason = scenario.equals("reverse-no-reason") ? " " : "บันทึกผิดคน";
            var reversed = service.reverse(result, new Permission(), reason, 9L);
            if (scenario.equals("reverse-twice")) service.reverse(result, new Permission(), reason, 9L);
            var allocs = allocations.arguments("save").stream().map(a -> (CommissionAllocationLedger) a[0]).toList();
            var entries = ledger.arguments("save").stream().map(a -> (CourseLedger) a[0]).toList();
            var lastEntitlement = (CourseEntitlement) entitlements.arguments("save").get(entitlements.arguments("save").size() - 1)[0];
            var cancellation = (TransactionCancellation) cancellationRepo.arguments("save").get(0)[0];
            resultMap.put("usageStatus", reversed.getUsageStatus());
            resultMap.put("allocationTypes", allocs.stream().map(a -> a.getEntryType().name()).toList());
            resultMap.put("reversalGross", allocs.get(1).getGrossCommissionAllocation());
            resultMap.put("reversalFee", allocs.get(1).getTreatmentFeeAmount());
            resultMap.put("reversalOwnerNet", allocs.get(1).getOwnerNetCommission());
            resultMap.put("reversalLinked", allocs.get(1).getAdjustmentReference() == allocs.get(0));
            resultMap.put("ledgerTypes", entries.stream().map(e -> e.getEntryType().name()).toList());
            resultMap.put("ledgerReversalLinked", entries.get(1).getReversesLedger() == entries.get(0));
            resultMap.put("reversalQty", entries.get(1).getQtyChange());
            resultMap.put("visitsUsed", course.getVisitsUsed());
            resultMap.put("remainingVisits", course.getRemainingVisits());
            resultMap.put("grossAllocatedTotal", course.getGrossCommissionAllocatedTotal());
            resultMap.put("ownerNetTotal", course.getOwnerNetCommissionReleasedTotal());
            resultMap.put("feeTotal", course.getSubstituteTreatmentFeeTotal());
            resultMap.put("outstanding", course.getCommissionOutstanding());
            resultMap.put("entitlementUsed", lastEntitlement.getUsedVisits());
            resultMap.put("cancellationType", cancellation.getCancellationType());
            return resultMap;
        });
    }

    @GetMapping("/print/{scenario}")
    public Map<String, Object> print(@PathVariable String scenario) {
        var prints = new V2RepositoryStub<>(CourseUsagePrintRepository.class);
        var events = new V2RepositoryStub<>(CourseUsagePrintEventRepository.class);
        var service = new CourseUsagePrintService(prints.proxy(), events.proxy());
        Course course = lockedCourse(); course.setCourseNo("RE00000010"); course.setCourseName("คอร์สทดสอบ");
        var usage = new CourseVisitUsage();
        usage.setCourse(course); usage.setVisitDate(LocalDate.of(2026, 9, 1).atStartOfDay());
        usage.setUsageStatus(scenario.equals("issue-reversed-usage") ? UsageStatus.Reversed : UsageStatus.Completed);
        if (scenario.equals("issue-second-version")) prints.on("countByVisitUsageId", args -> 1L);
        var existing = new CourseUsagePrint();
        prints.on("findById", args -> scenario.equals("reprint") ? Optional.of(existing) : Optional.empty());
        return V2RepositoryStub.outcome(() -> {
            if (scenario.startsWith("reprint")) {
                var event = service.reprint(1L, 9L);
                return Map.of("eventType", event.getEventType(), "printedBy", event.getPrintedBy(),
                        "sameDocument", event.getPrint() == existing);
            }
            var print = service.issue(usage, 9L);
            var event = (CourseUsagePrintEvent) events.arguments("save").get(0)[0];
            return Map.of("version", print.getDocumentVersion(), "eventType", event.getEventType(),
                    "issuedBy", print.getIssuedBy(), "sameUsage", print.getVisitUsage() == usage,
                    "snapshotHasCourseNo", print.getDocumentSnapshot().contains("RE00000010"));
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
