package com.sabyeguyz.clinicv2.course;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.patient.Patient;

class CourseDirectExtensionTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final BranchAccessPolicy access = mock(BranchAccessPolicy.class);
    private final Course course = mock(Course.class);
    private final Patient owner = mock(Patient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentActor actor = mock(CurrentActor.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final CourseExtensionController controller = new CourseExtensionController(jdbc, courses, access, actor, audit);

    @BeforeEach
    void setup() {
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(course.getPurchaserPatient()).thenReturn(owner);
        when(owner.getId()).thenReturn(2L);
        when(course.getId()).thenReturn(1L);
        when(actor.requireId()).thenReturn(7L);
        when(course.getCourseStatus()).thenReturn(CourseStatus.Active);
        when(course.getExpiryDate()).thenReturn(ClinicTime.today().plusDays(10));
    }

    private CourseExtensionController.DirectRequest request(int value, String unit, Long patientId,
            LocalDate oldExpiry, LocalDate newExpiry) {
        return new CourseExtensionController.DirectRequest(1L, patientId, value, unit, "", oldExpiry, newExpiry, true);
    }

    @Test
    void previewsWithoutChangingCourseOrCreatingARequest() {
        var preview = controller.previewDirect(request(1, "Month", 2L, null, null));
        assertEquals(course.getExpiryDate(), preview.oldExpiry());
        assertEquals(preview.oldExpiry().plusMonths(1), preview.newExpiry());
        verify(courses, never()).save(any());
        verify(courses, never()).findByIdForUpdate(any());
        verify(access).requireAccess(course.getBranch());
    }

    @Test
    void expiredCourseStartsFromToday() {
        when(course.getExpiryDate()).thenReturn(ClinicTime.today().minusDays(5));
        assertEquals(ClinicTime.today().plusDays(10),
                controller.previewDirect(request(10, "Day", 2L, null, null)).newExpiry());
    }

    @Test
    void sharedRecipientCannotExtendTheOwnersCourse() {
        var failure = assertThrows(ResponseStatusException.class,
                () -> controller.previewDirect(request(1, "Month", 3L, null, null)));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
    }

    @Test
    void rejectsCancelledCourse() {
        when(course.getCourseStatus()).thenReturn(CourseStatus.Cancelled);
        assertThrows(IllegalArgumentException.class,
                () -> controller.previewDirect(request(1, "Month", 2L, null, null)));
    }

    @Test
    void validatesDurationAndOwner() {
        for (var invalid : new CourseExtensionController.DirectRequest[] {
                request(0, "Day", 2L, null, null), request(100, "Day", 2L, null, null),
                request(13, "Month", 2L, null, null), request(2, "Year", 2L, null, null),
                request(1, "invalid", 2L, null, null), request(1, "Month", null, null, null) }) {
            assertThrows(IllegalArgumentException.class, () -> controller.previewDirect(invalid));
        }
    }

    @Test
    void rejectsStalePreviewAndLocksBeforeCommitting() {
        var failure = assertThrows(ResponseStatusException.class,
                () -> controller.extendDirect(request(1, "Month", 2L,
                        course.getExpiryDate().minusDays(1), course.getExpiryDate().plusMonths(1))));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(courses).findByIdForUpdate(1L);
        verify(courses, never()).save(any());
    }

    @Test
    void bothDirectEndpointsRequireManager() throws Exception {
        for (String method : new String[] { "previewDirect", "extendDirect" }) {
            assertEquals("hasRole('MANAGER')", CourseExtensionController.class
                    .getMethod(method, CourseExtensionController.DirectRequest.class)
                    .getAnnotation(PreAuthorize.class).value());
        }
    }

    @Test
    void commitsDirectlyWithoutInsertingARequest() {
        LocalDate before = course.getExpiryDate();
        var result = controller.extendDirect(request(1, "Month", 2L, before, before.plusMonths(1)));
        assertEquals(before.plusMonths(1), result.newExpiry());
        verify(course).setExpiryDate(result.newExpiry());
        verify(courses).save(course);
        verify(jdbc).queryForObject(startsWith("INSERT INTO course_extension ("), eq(Long.class), any(Object[].class));
        verify(jdbc, never()).queryForObject(startsWith("INSERT INTO course_extension_request"), eq(Long.class), any(Object[].class));
        verify(audit).record(eq("course"), eq("1"), eq(AuditAction.UPDATE), anyString(), anyString(), eq(7L));
    }

    @Test
    void rejectsPendingRequestsWithoutWriting() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        LocalDate before = course.getExpiryDate();
        var failure = assertThrows(ResponseStatusException.class,
                () -> controller.extendDirect(request(1, "Month", 2L, before, before.plusMonths(1))));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(courses, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void requiresConfirmationBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> controller.extendDirect(
                new CourseExtensionController.DirectRequest(1L, 2L, 1, "Month", "", null, null, false)));
        verifyNoInteractions(courses, jdbc, audit);
    }

    @Test
    void therapistCannotCallEitherDirectEndpoint() {
        var factory = new ProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        var secured = (CourseExtensionController) factory.getProxy();
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "3", "", java.util.List.of(new SimpleGrantedAuthority("ROLE_THERAPIST"))));
            var request = request(1, "Month", 2L, null, null);
            assertThrows(AccessDeniedException.class, () -> secured.previewDirect(request));
            assertThrows(AccessDeniedException.class, () -> secured.extendDirect(request));
            verifyNoInteractions(courses, jdbc, audit);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
