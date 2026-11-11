package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.PrintEventType;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final AppUserRepository users;

    public CourseUsagePrintController(CourseUsagePrintRepository prints, CourseUsagePrintEventRepository events,
                                      CourseVisitUsageRepository usages, CourseRepository courses,
                                      CourseUsagePrintService printService,
                                      BranchAccessPolicy branchAccess, CurrentActor actor, AppUserRepository users) {
        this.prints = prints;
        this.events = events;
        this.usages = usages;
        this.courses = courses;
        this.printService = printService;
        this.branchAccess = branchAccess;
        this.actor = actor;
        this.users = users;
    }

    public record PrintEventResponse(Long id, PrintEventType type, Instant printedAt, String printedBy,
                                     Long printedById, Long printId, int version) {}

    public record PrintDocumentResponse(Long id, Long courseId, Long usageId, int version, Instant issuedAt,
                                        String issuedBy, String documentSnapshot,
                                        List<PrintEventResponse> events, Long issuedById) {}

    @GetMapping("/api/course-purchases/{courseId}/usage-prints")
    public List<PrintDocumentResponse> usagePrints(@PathVariable Long courseId) {
        List<CourseUsagePrint> documents = prints.findByCourseIdOrderByIssuedAtDescDocumentVersionDesc(courseId);
        if (documents.isEmpty()) {
            var course = courses.findById(courseId).orElseThrow(() -> notFound("ไม่พบคอร์ส"));
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
        // The usage transaction also locks its course. Serialize explicit revisions
        // with it so two requests cannot allocate the same document version.
        courses.findByIdForUpdate(usage.getCourse().getId()).orElseThrow(() -> notFound("ไม่พบคอร์ส"));
        return toResponse(printService.issue(usage, actor.requireId()));
    }

    private CourseUsagePrint findPrint(Long printId) {
        CourseUsagePrint print = prints.findById(printId)
                .orElseThrow(() -> notFound("ไม่พบใบตัดคอร์ส"));
        branchAccess.requireAccess(print.getCourse().getBranch());
        return print;
    }

    private PrintDocumentResponse toResponse(CourseUsagePrint print) {
        // Names are resolved for display only. Stored document snapshots and actor IDs stay immutable.
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        List<PrintEventResponse> printEvents = events.findByPrintIdOrderByPrintedAtAsc(print.getId()).stream()
                .map(event -> new PrintEventResponse(event.getId(), event.getEventType(), event.getPrintedAt(),
                        names.computeIfAbsent(event.getPrintedBy(), this::actorName), event.getPrintedBy(),
                        print.getId(), print.getDocumentVersion()))
                .toList();
        return new PrintDocumentResponse(print.getId(), print.getCourse().getId(), print.getVisitUsage().getId(),
                print.getDocumentVersion(), print.getIssuedAt(), names.computeIfAbsent(print.getIssuedBy(), this::actorName),
                print.getDocumentSnapshot(), printEvents, print.getIssuedBy());
    }

    private String actorName(Long actorId) {
        if (actorId == null) return "ไม่ทราบผู้ใช้";
        return users.findById(actorId).map(user -> {
            if (user.getStaff() != null) {
                String name = java.util.stream.Stream.of(user.getStaff().getStaffFname(), user.getStaff().getStaffLname())
                        .filter(value -> value != null && !value.isBlank())
                        .collect(java.util.stream.Collectors.joining(" "));
                if (!name.isBlank()) return name;
            }
            return user.getUsername();
        }).filter(name -> name != null && !name.isBlank()).orElse("ผู้ใช้ #" + actorId);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
