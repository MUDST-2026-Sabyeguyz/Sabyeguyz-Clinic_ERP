package com.sabyeguyz.clinicv2.api;

import com.sabyeguyz.clinicv2.appointment.AppointmentNotFoundException;
import com.sabyeguyz.clinicv2.appointment.PatientVisitNotFoundException;
import com.sabyeguyz.clinicv2.branch.BranchNotFoundException;
import com.sabyeguyz.clinicv2.patient.PatientNotFoundException;
import com.sabyeguyz.clinicv2.staff.StaffNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns the exceptions the v2 services throw into the same JSON error body the old API used
 * ({"message": ...}): a rule violation (IllegalArgumentException) is 400, a missing record 404,
 * a state or uniqueness conflict 409.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiErrorResponse("ข้อมูลไม่ถูกต้อง", fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(safe(exception)));
    }

    /** Keeps the {"message"} body for status exceptions (403, 401, 429 ...) thrown by controllers and policies. */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> status(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(
                exception.getReason() == null ? "ไม่สามารถทำรายการได้" : exception.getReason()));
    }

    /** Rule messages are written for users; a message that names an internal class or enum is replaced. */
    private static String safe(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.startsWith("No enum constant") || message.contains("com.sabyeguyz")
                || message.contains("java.") || message.contains("org.springframework")) {
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).warn("Rejected request: {}", message);
            return "ข้อมูลที่ส่งมาไม่ถูกต้อง";
        }
        return message;
    }

    @ExceptionHandler({PatientNotFoundException.class, BranchNotFoundException.class, StaffNotFoundException.class,
            AppointmentNotFoundException.class, PatientVisitNotFoundException.class})
    ResponseEntity<ApiErrorResponse> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(safe(exception)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> integrity(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("ข้อมูลซ้ำหรือขัดกับข้อมูลที่มีอยู่แล้ว"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> concurrentUpdate(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("ข้อมูลถูกแก้ไขโดยผู้ใช้อื่นไปแล้ว กรุณาโหลดข้อมูลใหม่แล้วลองอีกครั้ง"));
    }

    /** 409: Reports database lock conflicts without exposing internal errors. */
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> lockConflict(
            org.springframework.dao.PessimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "มีรายการอื่นกำลังประมวลผลอยู่ กรุณาลองใหม่อีกครั้ง"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "รูปแบบข้อมูลที่ส่งมาไม่ถูกต้อง เช่น วันที่ต้องอยู่ในรูปแบบ 2026-09-01T09:00:00+07:00"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("ค่าของพารามิเตอร์ " + exception.getName() + " ไม่ถูกต้อง"));
    }
}
