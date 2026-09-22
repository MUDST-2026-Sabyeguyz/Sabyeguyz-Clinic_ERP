package com.sabyeguyz.clinic.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseManagementController {
    private final CourseRepository repository;

    public CourseManagementController(CourseRepository repository) { this.repository = repository; }

    public record Request(@NotBlank @Size(max = 40) String code,
                          @NotBlank @Size(max = 160) String name,
                          @NotNull @DecimalMin("0.0") BigDecimal price,
                          @Min(1) int sessions,
                          @Min(0) int bonusSessions,
                          @Min(1) int validityDays,
                          Boolean active) {}

    public record Response(Long id, String code, String name, BigDecimal price, int sessions,
                           int bonusSessions, int validityDays, boolean active) {}

    @GetMapping
    List<Response> list() {
        return repository.findAllByOrderByName().stream().map(CourseManagementController::response).toList();
    }

    @GetMapping("/{id}")
    Response get(@PathVariable Long id) { return response(find(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request request) {
        String code = normalize(request.code());
        if (repository.findByCode(code).isPresent()) throw new IllegalArgumentException("รหัส Course ซ้ำ");
        Course course = new Course(code, request.name().trim(), request.price(), request.sessions(),
                request.bonusSessions(), request.validityDays());
        applyActive(course, request.active());
        return response(repository.save(course));
    }

    @PutMapping("/{id}")
    Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Course course = find(id);
        String code = normalize(request.code());
        if (repository.findByCode(code).filter(found -> !found.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("รหัส Course ซ้ำ");
        course.update(code, request.name().trim(), request.price(), request.sessions(),
                request.bonusSessions(), request.validityDays());
        applyActive(course, request.active());
        return response(repository.save(course));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable Long id) {
        Course course = find(id);
        course.deactivate();
        repository.save(course);
    }

    private Course find(Long id) { return repository.findById(id).orElseThrow(CourseNotFoundException::new); }

    private static void applyActive(Course course, Boolean active) {
        if (active != null) { if (active) course.activate(); else course.deactivate(); }
    }

    private static String normalize(String value) { return value.trim().toUpperCase(); }

    private static Response response(Course course) {
        return new Response(course.getId(), course.getCode(), course.getName(), course.getPrice(), course.getSessions(),
                course.getBonusSessions(), course.getValidityDays(), course.isActive());
    }
}
