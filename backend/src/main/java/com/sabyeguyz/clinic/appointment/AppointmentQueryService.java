package com.sabyeguyz.clinic.appointment;

import com.sabyeguyz.clinic.branch.BranchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");

    private final AppointmentRepository appointmentRepository;
    private final BranchRepository branchRepository;

    public AppointmentQueryService(AppointmentRepository appointmentRepository, BranchRepository branchRepository) {
        this.appointmentRepository = appointmentRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public AppointmentSearchResponse search(String branchCode, LocalDate date, Long therapistId,
                                            AppointmentStatus status, int page, int size) {
        validatePagination(page, size);
        Long branchId = resolveBranchId(branchCode);
        Instant from = date == null ? null : date.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        return AppointmentSearchResponse.from(appointmentRepository.search(
                branchId, therapistId, status, from, to,
                PageRequest.of(page, size, Sort.by("startAt").ascending().and(Sort.by("id").ascending()))));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(long id) {
        return appointmentRepository.findById(id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
    }

    private Long resolveBranchId(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return null;
        }
        return branchRepository.findByBranchCodeAndActiveTrue(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"))
                .getId();
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size ต้องอยู่ระหว่าง 1 ถึง " + MAX_PAGE_SIZE);
        }
    }
}