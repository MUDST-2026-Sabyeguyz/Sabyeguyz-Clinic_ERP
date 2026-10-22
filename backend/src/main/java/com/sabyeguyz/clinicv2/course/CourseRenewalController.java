package com.sabyeguyz.clinicv2.course;

import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Dedicated expiry endpoints keep renewal separate from selling a new course. */
@RestController
@RequestMapping("/api/course-purchases/{id}/renewals")
public class CourseRenewalController {
    private final CourseRenewalService renewals;
    public CourseRenewalController(CourseRenewalService renewals) { this.renewals = renewals; }
    @PostMapping
    public CourseRenewalService.RenewalResult renew(@PathVariable Long id,
            @RequestBody CourseRenewalService.RenewalRequest request) { return renewals.renew(id, request); }
    @GetMapping
    public List<CourseRenewalService.RenewalHistory> history(@PathVariable Long id) { return renewals.history(id); }
}
