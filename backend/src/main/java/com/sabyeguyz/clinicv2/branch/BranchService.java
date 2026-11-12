package com.sabyeguyz.clinicv2.branch;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back Office branch management (Clinic ERP Workflow Brief §2).
 * branchCode is immutable after creation: it is baked into every patient's
 * HN and must never change under an existing branch.
 */
@Service
public class BranchService {
    private static final java.util.regex.Pattern BRANCH_CODE = java.util.regex.Pattern.compile("\\d{2}");

    private final BranchRepository repository;

    public BranchService(BranchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Branch> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Branch get(Long id) {
        return find(id);
    }

    /** Next free 2-digit branch code (01..99), one above the current highest. */
    @Transactional(readOnly = true)
    public String nextBranchCode() {
        String max = repository.findMaxBranchCode();
        int next = max == null ? 1 : Integer.parseInt(max.trim()) + 1;
        if (next > 99) {
            throw new IllegalArgumentException("รหัสสาขาเต็มแล้ว (สูงสุด 99 สาขา)");
        }
        return String.format("%02d", next);
    }

    @Transactional
    public Branch create(String branchCode, String name, String address, String phone, Long actorId) {
        String code = normalizeCode(branchCode);
        if (repository.findByBranchCode(code).isPresent()) {
            throw new IllegalArgumentException("รหัสสาขาซ้ำ");
        }
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(requireText(name, "branchName"));
        branch.setAddress(requireText(address, "address"));
        branch.setPhone(requireText(phone, "phone"));
        branch.setBranchStatus(true);
        branch.setBranchCreateAt(Instant.now());
        branch.setBranchCreateBy(actorId);
        return repository.save(branch);
    }

    @Transactional
    public Branch update(Long id, String name, String address, String phone, Long actorId) {
        Branch branch = find(id);
        branch.setBranchName(requireText(name, "branchName"));
        branch.setAddress(requireText(address, "address"));
        branch.setPhone(requireText(phone, "phone"));
        branch.setBranchUpdateAt(Instant.now());
        branch.setBranchUpdateBy(actorId);
        return repository.save(branch);
    }

    /** The optional branch settings. Times are "HH:mm"; the booking window needs both ends. */
    public record Settings(String email, String licenseNo, String bookingOpenTime, String bookingCloseTime,
                           Integer defaultAppointmentMinutes) {}

    @Transactional
    public Branch applySettings(Long id, Settings settings, Long actorId) {
        Branch branch = find(id);
        String email = blankToNull(settings.email());
        if (email != null && (email.length() > 254
                || !email.matches("^[^@.\\s]+(\\.[^@.\\s]+)*@[^@.\\s]+(\\.[^@.\\s]+)+$"))) {
            throw new IllegalArgumentException("รูปแบบอีเมลสาขาไม่ถูกต้อง");
        }
        java.time.LocalTime open = parseTime(settings.bookingOpenTime(), "เวลาเริ่มรับนัด");
        java.time.LocalTime close = parseTime(settings.bookingCloseTime(), "เวลาสิ้นสุดรับนัด");
        if ((open == null) != (close == null)) {
            throw new IllegalArgumentException("ต้องระบุช่วงเปิดรับนัดทั้งเวลาเริ่มและเวลาสิ้นสุด");
        }
        if (open != null && !open.isBefore(close)) {
            throw new IllegalArgumentException("เวลาเริ่มรับนัดต้องอยู่ก่อนเวลาสิ้นสุด");
        }
        Integer minutes = settings.defaultAppointmentMinutes();
        if (minutes != null && minutes < 1) {
            throw new IllegalArgumentException("ระยะเวลานัดเริ่มต้นต้องเป็นจำนวนเต็มอย่างน้อย 1 นาที");
        }
        String license = blankToNull(settings.licenseNo());
        if (license != null && license.length() > 50) {
            throw new IllegalArgumentException("เลขใบอนุญาตยาวเกิน 50 ตัวอักษร");
        }
        branch.setBranchEmail(email);
        branch.setLicenseNo(license);
        branch.setBookingOpenTime(open);
        branch.setBookingCloseTime(close);
        branch.setDefaultAppointmentMinutes(minutes);
        branch.setBranchUpdateAt(Instant.now());
        branch.setBranchUpdateBy(actorId);
        return repository.save(branch);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static java.time.LocalTime parseTime(String value, String field) {
        String text = blankToNull(value);
        if (text == null) return null;
        try {
            return java.time.LocalTime.parse(text);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " ต้องอยู่ในรูปแบบ HH:mm");
        }
    }

    @Transactional
    public Branch deactivate(Long id, Long actorId) {
        Branch branch = find(id);
        branch.setBranchStatus(false);
        branch.setBranchUpdateAt(Instant.now());
        branch.setBranchUpdateBy(actorId);
        return repository.save(branch);
    }

    @Transactional
    public Branch activate(Long id, Long actorId) {
        Branch branch = find(id);
        branch.setBranchStatus(true);
        branch.setBranchUpdateAt(Instant.now());
        branch.setBranchUpdateBy(actorId);
        return repository.save(branch);
    }

    private Branch find(Long id) {
        return repository.findById(id).orElseThrow(BranchNotFoundException::new);
    }

    private static String normalizeCode(String branchCode) {
        if (branchCode == null || !BRANCH_CODE.matcher(branchCode.trim()).matches()) {
            throw new IllegalArgumentException("branchCode ต้องเป็นตัวเลข 2 หลัก");
        }
        return branchCode.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
