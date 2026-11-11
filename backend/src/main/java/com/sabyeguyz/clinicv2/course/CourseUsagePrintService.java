package com.sabyeguyz.clinicv2.course;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.enums.PrintEventType;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Printable document of a course usage. Issuing creates the next document
 * version with a frozen snapshot and its one Issued event; each further paper
 * copy of that version is a Reprint event.
 */
@Service
public class CourseUsagePrintService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CourseUsagePrintRepository prints;
    private final CourseUsagePrintEventRepository events;
    private final CourseVisitUsageRepository usages;
    private final CourseLedgerRepository ledger;

    public CourseUsagePrintService(CourseUsagePrintRepository prints, CourseUsagePrintEventRepository events,
                                    CourseVisitUsageRepository usages, CourseLedgerRepository ledger) {
        this.usages = usages;
        this.ledger = ledger;
        this.prints = prints;
        this.events = events;
    }

    @Transactional
    public CourseUsagePrint issue(CourseVisitUsage usage, Long actorId) {
        require(usage != null, "ต้องระบุรายการใช้สิทธิ์คอร์ส");
        require(usage.getUsageStatus() == UsageStatus.Completed, "พิมพ์เอกสารได้เฉพาะรายการใช้สิทธิ์ที่สำเร็จ");

        CourseUsagePrint print = new CourseUsagePrint();
        print.setVisitUsage(usage);
        print.setCourse(usage.getCourse());
        print.setIssuedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        print.setIssuedBy(actorId);
        print.setDocumentVersion((int) prints.countByVisitUsageId(usage.getId()) + 1);
        print.setDocumentSnapshot(snapshotOf(usage, historyOf(usage), movementsOf(usage), print.getIssuedAt()));
        CourseUsagePrint saved = prints.save(print);
        recordEvent(saved, PrintEventType.Issued, actorId);
        return saved;
    }

    @Transactional
    public CourseUsagePrintEvent reprint(Long printId, Long actorId) {
        CourseUsagePrint print = prints.findById(printId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบเอกสารที่พิมพ์"));
        return recordEvent(print, PrintEventType.Reprint, actorId);
    }

    private CourseUsagePrintEvent recordEvent(CourseUsagePrint print, PrintEventType type, Long actorId) {
        CourseUsagePrintEvent event = new CourseUsagePrintEvent();
        event.setPrint(print);
        event.setPrintedAt(Instant.now());
        event.setPrintedBy(actorId);
        event.setEventType(type);
        return events.save(event);
    }

    /** Every session of the course given up to and including this one, whoever used it (the printed history). */
    private List<Map<String, Object>> historyOf(CourseVisitUsage usage) {
        // Reversed sessions remain part of the cumulative evidence; their status explains
        // the returned entitlement instead of silently removing the original treatment.
        List<CourseVisitUsage> completed = new ArrayList<>(usages.findByCourseIdOrderByIdDesc(usage.getCourse().getId()));
        if (completed.stream().noneMatch(other -> other == usage || (other.getId() != null && other.getId().equals(usage.getId())))) {
            completed.add(usage);
        }
        completed.sort(Comparator.comparingInt(CourseVisitUsage::getUsageSequence));
        List<Map<String, Object>> history = new ArrayList<>();
        for (CourseVisitUsage entry : completed) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sequence", entry.getUsageSequence());
            line.put("visitDate", String.valueOf(entry.getVisitDate().toLocalDate()));
            line.put("patientId", entry.getPatient() == null ? null : entry.getPatient().getId());
            line.put("patientName", patientName(entry.getPatient()));
            line.put("patientHn", entry.getPatient() == null ? null : entry.getPatient().getPatientHn());
            line.put("treatingStaffId", entry.getTreatingStaff() == null ? null : entry.getTreatingStaff().getId());
            line.put("treatingStaffName", staffName(entry.getTreatingStaff()));
            line.put("quantity", entry.getVisitQty());
            line.put("status", entry.getUsageStatus().name());
            line.put("replacesUsageId", entry.getReplacesUsage() == null ? null : entry.getReplacesUsage().getId());
            history.add(line);
        }
        return history;
    }

    private List<Map<String, Object>> movementsOf(CourseVisitUsage usage) {
        List<Map<String, Object>> movements = new ArrayList<>();
        List<CourseLedger> entries = new ArrayList<>(ledger.findByCourseIdOrderByIdDesc(usage.getCourse().getId()));
        entries.sort(Comparator.comparing(CourseLedger::getEventAt,
                Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(CourseLedger::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (CourseLedger entry : entries) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", entry.getId());
            line.put("type", entry.getEntryType().name());
            line.put("quantity", entry.getQtyChange());
            line.put("balanceAfter", entry.getBalanceAfter());
            line.put("patientId", entry.getPatient() == null ? null : entry.getPatient().getId());
            line.put("patientName", patientName(entry.getPatient()));
            line.put("patientHn", entry.getPatient() == null ? null : entry.getPatient().getPatientHn());
            line.put("occurredAt", entry.getEventAt() == null ? null : entry.getEventAt().toString());
            line.put("reason", entry.getRemark());
            line.put("reversesLedgerId", entry.getReversesLedger() == null ? null : entry.getReversesLedger().getId());
            movements.add(line);
        }
        return movements;
    }

    private static String snapshotOf(CourseVisitUsage usage, List<Map<String, Object>> history,
                                     List<Map<String, Object>> movements, Instant issuedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        var course = usage.getCourse();
        snapshot.put("issuedAt", issuedAt.toString());
        snapshot.put("purchaseDate", course.getReceipt() == null || course.getReceipt().getSaleDate() == null
                ? null : course.getReceipt().getSaleDate().toString());
        snapshot.put("totalVisits", course.getTotalVisits());
        snapshot.put("expiryDate", course.getExpiryDate() == null ? null : course.getExpiryDate().toString());
        snapshot.put("buyer", patientDetails(course.getPurchaserPatient()));
        snapshot.put("actualPatient", patientDetails(usage.getPatient()));
        Map<String, Object> treating = new LinkedHashMap<>();
        treating.put("id", usage.getTreatingStaff() == null ? null : usage.getTreatingStaff().getId());
        treating.put("name", staffName(usage.getTreatingStaff()));
        snapshot.put("treatingStaff", treating);
        Map<String, Object> branch = new LinkedHashMap<>();
        if (course.getBranch() != null) {
            branch.put("code", course.getBranch().getBranchCode());
            branch.put("name", course.getBranch().getBranchName());
            branch.put("address", course.getBranch().getAddress());
            branch.put("phone", course.getBranch().getPhone());
            branch.put("email", course.getBranch().getBranchEmail());
            branch.put("licenseNo", course.getBranch().getLicenseNo());
        }
        snapshot.put("branch", branch);
        snapshot.put("courseNo", usage.getCourse().getCourseNo());
        snapshot.put("courseName", usage.getCourse().getCourseName());
        snapshot.put("visitDate", String.valueOf(usage.getVisitDate().toLocalDate()));
        snapshot.put("visitQty", usage.getVisitQty());
        snapshot.put("treatingStaffId", usage.getTreatingStaff() == null ? null : usage.getTreatingStaff().getId());
        snapshot.put("caseOwnerStaffId", usage.getOwnerStaff() == null ? null : usage.getOwnerStaff().getId());
        snapshot.put("visitsUsed", usage.getCourse().getVisitsUsed());
        snapshot.put("remainingVisits", usage.getCourse().getRemainingVisits());
        snapshot.put("history", history);
        snapshot.put("movements", movements);
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> patientDetails(com.sabyeguyz.clinicv2.patient.Patient patient) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", patient == null ? null : patient.getId());
        result.put("hn", patient == null ? null : patient.getPatientHn());
        result.put("name", patientName(patient));
        return result;
    }

    private static String patientName(com.sabyeguyz.clinicv2.patient.Patient patient) {
        if (patient == null) return null;
        return java.util.stream.Stream.of(patient.getPatientFname(), patient.getPatientMiddleName(), patient.getPatientLname())
                .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" "));
    }

    private static String staffName(com.sabyeguyz.clinicv2.staff.Staff staff) {
        if (staff == null) return null;
        return java.util.stream.Stream.of(staff.getStaffFname(), staff.getStaffLname())
                .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" "));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
