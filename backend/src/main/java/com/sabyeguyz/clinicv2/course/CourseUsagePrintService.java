package com.sabyeguyz.clinicv2.course;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.enums.PrintEventType;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    public CourseUsagePrintService(CourseUsagePrintRepository prints, CourseUsagePrintEventRepository events) {
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
        print.setIssuedAt(Instant.now());
        print.setIssuedBy(actorId);
        print.setDocumentVersion((int) prints.countByVisitUsageId(usage.getId()) + 1);
        print.setDocumentSnapshot(snapshotOf(usage));
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

    private static String snapshotOf(CourseVisitUsage usage) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("courseNo", usage.getCourse().getCourseNo());
        snapshot.put("courseName", usage.getCourse().getCourseName());
        snapshot.put("visitDate", String.valueOf(usage.getVisitDate().toLocalDate()));
        snapshot.put("visitQty", usage.getVisitQty());
        snapshot.put("treatingStaffId", usage.getTreatingStaff() == null ? null : usage.getTreatingStaff().getId());
        snapshot.put("caseOwnerStaffId", usage.getOwnerStaff() == null ? null : usage.getOwnerStaff().getId());
        snapshot.put("visitsUsed", usage.getCourse().getVisitsUsed());
        snapshot.put("remainingVisits", usage.getCourse().getRemainingVisits());
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
