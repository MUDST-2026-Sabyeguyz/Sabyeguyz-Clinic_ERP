package com.sabyeguyz.clinic.catalog;
import com.sabyeguyz.clinic.therapist.Therapist;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@org.springframework.context.annotation.Profile("!database-v2")
@RestController @RequestMapping("/api/catalog") public class CatalogController {
 private final CourseRepository courses; private final ServiceItemRepository services; private final TherapistRepository therapists;
 public CatalogController(CourseRepository courses, ServiceItemRepository services, TherapistRepository therapists){this.courses=courses;this.services=services;this.therapists=therapists;}
 public record ServiceResponse(Long id,String code,String name,int durationMinutes,BigDecimal price){}
 public record TherapistResponse(Long id,String code,String fullName,String branchCode){}
 @GetMapping("/courses") List<Course> courses(){return courses.findByActiveTrueOrderByName();}
 @GetMapping("/services") List<ServiceResponse> services(){return services.findByActiveTrueOrderByName().stream().map(s->new ServiceResponse(s.getId(),s.getCode(),s.getName(),s.getDefaultDurationMinutes(),s.getPrice())).toList();}
 @GetMapping("/therapists") List<TherapistResponse> therapists(){return therapists.findByActiveTrueOrderByFullName().stream().map(t->new TherapistResponse(t.getId(),t.getCode(),t.getFullName(),t.getBranch().getCode())).toList();}
}
