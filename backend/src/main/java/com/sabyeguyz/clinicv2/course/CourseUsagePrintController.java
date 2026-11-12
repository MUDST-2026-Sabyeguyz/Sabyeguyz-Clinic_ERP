package com.sabyeguyz.clinicv2.course;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.PrintEventType;

/** HTTP endpoints for frozen course usage documents and their print history. */
@RestController
@Transactional(readOnly = true)
public class CourseUsagePrintController {
    private final CourseUsagePrintRepository prints;
    private final CourseUsagePrintEventRepository events;
    private final CourseVisitUsageRepository usages;
    private final CourseRepository courses;
    private final CourseUsagePrintService printService;
    private final BranchAccessPolicy branchAccess;
    private final CurrentActor actor;

    public CourseUsagePrintController(
            CourseUsagePrintRepository prints,
            CourseUsagePrintEventRepository events,
            CourseVisitUsageRepository usages,
            CourseRepository courses,
            CourseUsagePrintService printService,
            BranchAccessPolicy branchAccess,
            CurrentActor actor) {
        this.prints = prints;
        this.events = events;
        this.usages = usages;
        this.courses = courses;
        this.printService = printService;
        this.branchAccess = branchAccess;
        this.actor = actor;
    }

    public record PrintEventResponse(
            Long id,
            PrintEventType type,
            Instant printedAt,
            String printedBy) {}

    public record PrintDocumentResponse(
            Long id,
            Long courseId,
            Long usageId,
            int version,
            Instant issuedAt,
            String issuedBy,
            String documentSnapshot,
            List<PrintEventResponse> events) {}

    @GetMapping("/api/course-purchases/{courseId}/usage-prints")
    public List<PrintDocumentResponse> usagePrints(@PathVariable Long courseId) {
        List<CourseUsagePrint> documents = prints.findByCourseIdOrderByIssuedAtDescDocumentVersionDesc(courseId);

        if (documents.isEmpty()) {
            var course = courses.findById(courseId)
                    .orElseThrow(() -> notFound("ไม่พบคอร์ส"));

            branchAccess.requireAccess(course.getBranch());

            return List.of();
        }

        branchAccess.requireAccess(documents.get(0).getCourse().getBranch());

        return documents.stream().map(this::toResponse).toList();
    }

    @GetMapping("/api/course-usage-prints/{printId}")
    public PrintDocumentResponse usagePrint(@PathVariable Long printId) {
        return toResponse(findPrint(printId));
    }

    @PostMapping("/api/course-usage-prints/{printId}/reprints")
    @Transactional
    public PrintDocumentResponse reprint(@PathVariable Long printId) {
        CourseUsagePrint print = findPrint(printId);

        printService.reprint(printId, actor.requireId());

        return toResponse(print);
    }

    @PostMapping("/api/course-usages/{usageId}/print-revisions")
    @Transactional
    public PrintDocumentResponse issueRevision(@PathVariable Long usageId) {
        CourseVisitUsage usage = usages.findById(usageId)
                .orElseThrow(() -> notFound("ไม่พบรายการใช้สิทธิ์คอร์ส"));

        branchAccess.requireAccess(usage.getCourse().getBranch());

        return toResponse(printService.issue(usage, actor.requireId()));
    }

    private CourseUsagePrint findPrint(Long printId) {
        CourseUsagePrint print = prints.findById(printId)
                .orElseThrow(() -> notFound("ไม่พบใบตัดคอร์ส"));

        branchAccess.requireAccess(print.getCourse().getBranch());

        return print;
    }

    private PrintDocumentResponse toResponse(CourseUsagePrint print) {
        List<PrintEventResponse> printEvents = events.findByPrintIdOrderByPrintedAtAsc(print.getId())
                .stream()
                .map(event -> new PrintEventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getPrintedAt(),
                        String.valueOf(event.getPrintedBy())))
                .toList();

        return new PrintDocumentResponse(
                print.getId(),
                print.getCourse().getId(),
                print.getVisitUsage().getId(),
                print.getDocumentVersion(),
                print.getIssuedAt(),
                String.valueOf(print.getIssuedBy()),
                print.getDocumentSnapshot(),
                printEvents);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
