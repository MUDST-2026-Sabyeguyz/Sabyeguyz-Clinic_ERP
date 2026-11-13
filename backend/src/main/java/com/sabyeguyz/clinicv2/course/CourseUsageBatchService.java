package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.treatment.Treatment;
import com.sabyeguyz.clinicv2.treatment.TreatmentRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CourseUsageBatchService {
    public record Item(
            Long coursePurchaseId,
            String note,
            Long treatmentId) {}

    private final CourseRepository courses;
    private final CourseVisitUsageService usageService;
    private final TreatmentRepository treatments;
    private final BranchAccessPolicy branchAccess;

    public CourseUsageBatchService(
            CourseRepository courses,
            CourseVisitUsageService usageService,
            TreatmentRepository treatments,
            BranchAccessPolicy branchAccess) {
        this.courses = courses;
        this.usageService = usageService;
        this.treatments = treatments;
        this.branchAccess = branchAccess;
    }

    public Map<Long, Course> lockCourses(List<Item> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("ต้องระบุรายการใช้สิทธิ์คอร์ส");
        }

        for (Item item : items) {
            if (item == null || item.coursePurchaseId() == null) {
                throw new IllegalArgumentException("ต้องระบุคอร์สทุกรายการ");
            }
        }

        Map<Long, Course> locked = new TreeMap<>();

        items.stream()
                .map(Item::coursePurchaseId)
                .distinct()
                .sorted()
                .forEach(id -> {
                    Course course = courses.findByIdForUpdate(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException("ไม่พบคอร์ส"));
                    branchAccess.requireAccess(
                            course.getBranch(),
                            "ใช้สิทธิ์ได้เฉพาะรายการของสาขาตัวเอง");
                    locked.put(id, course);
                });

        return locked;
    }

    public void recordForVisit(
            Map<Long, Course> lockedCourses,
            List<Item> items,
            PatientVisit visit,
            LocalDateTime usedAt,
            Long actorId) {
        if (visit == null || visit.getPatient() == null
                || visit.getTreatingStaff() == null) {
            throw new IllegalArgumentException(
                    "ต้องระบุ Visit ผู้ป่วย และผู้รักษาก่อนใช้สิทธิ์");
        }

        for (Item item : items) {
            Course course = lockedCourses.get(item.coursePurchaseId());
            if (course == null) {
                throw new IllegalArgumentException("คอร์สนี้ยังไม่ได้ล็อก");
            }
            if (!visit.getBranch().getId().equals(course.getBranch().getId())) {
                throw new IllegalArgumentException(
                        "คอร์สต้องอยู่สาขาเดียวกับ Visit");
            }

            Treatment treatment = item.treatmentId() == null
                    ? null
                    : treatments.findById(item.treatmentId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("ไม่พบ Treatment"));

            if (treatment == null) {
                treatment = new Treatment();
                treatment.setVisit(visit);
                treatment.setService(course.getService());
                treatment.setTreatingStaff(visit.getTreatingStaff());
                treatment.setNotes(
                        item.note() == null ? null : item.note().trim());
                treatment.setTreatmentCreateAt(Instant.now());
                treatment.setTreatmentCreateBy(actorId);
                treatment = treatments.save(treatment);
            }

            usageService.use(
                    course,
                    visit,
                    visit.getPatient(),
                    visit.getTreatingStaff(),
                    usedAt,
                    treatment,
                    actorId);
        }
    }
}