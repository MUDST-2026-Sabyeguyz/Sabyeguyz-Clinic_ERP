package com.sabyeguyz.clinic.api;

import com.sabyeguyz.clinic.patient.PatientNotFoundException;
import com.sabyeguyz.clinic.branch.BranchNotFoundException;
import com.sabyeguyz.clinic.staff.StaffNotFoundException;
import com.sabyeguyz.clinic.catalog.CourseNotFoundException;
import com.sabyeguyz.clinic.course.CourseBalanceException;
import com.sabyeguyz.clinic.course.CoursePurchaseNotFoundException;
import com.sabyeguyz.clinic.sale.SaleException;
import com.sabyeguyz.clinic.sale.SaleNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.sabyeguyz.clinic.appointment.AppointmentConflictException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.sabyeguyz.clinic.appointment.AppointmentNotFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiErrorResponse("ข้อมูลไม่ถูกต้อง", fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(PatientNotFoundException.class)
    ResponseEntity<ApiErrorResponse> patientNotFound(PatientNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(BranchNotFoundException.class)
    ResponseEntity<ApiErrorResponse> branchNotFound(BranchNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(StaffNotFoundException.class)
    ResponseEntity<ApiErrorResponse> staffNotFound(StaffNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }
    @ExceptionHandler(CourseNotFoundException.class)
    ResponseEntity<ApiErrorResponse> courseNotFound(CourseNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(CoursePurchaseNotFoundException.class)
    ResponseEntity<ApiErrorResponse> coursePurchaseNotFound(CoursePurchaseNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(CourseBalanceException.class)
    ResponseEntity<ApiErrorResponse> courseBalance(CourseBalanceException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(SaleNotFoundException.class)
    ResponseEntity<ApiErrorResponse> saleNotFound(SaleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(SaleException.class)
    ResponseEntity<ApiErrorResponse> saleConflict(SaleException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(AppointmentConflictException.class)
    ResponseEntity<ApiErrorResponse> appointmentConflict(AppointmentConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("รูปแบบข้อมูลที่ส่งมาไม่ถูกต้อง เช่น วันที่ต้องอยู่ในรูปแบบ 2026-09-01T09:00:00+07:00"));
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> appointmentNotFound(AppointmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("ค่าของพารามิเตอร์ " + exception.getName() + " ไม่ถูกต้อง"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> concurrentUpdate(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("ข้อมูลถูกแก้ไขโดยผู้ใช้อื่นไปแล้ว กรุณาโหลดข้อมูลใหม่แล้วลองอีกครั้ง"));
    }
}
