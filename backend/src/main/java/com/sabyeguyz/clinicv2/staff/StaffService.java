package com.sabyeguyz.clinicv2.staff;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.StaffPosition;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.commission.StaffPayAdjustmentService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Staff/Physiotherapist management (Clinic ERP Workflow Brief §2).
 * Termination (Data Dictionary, Staff.termination_date): after the termination date the staff member no longer earns
 * Owner commission and substitutes get the treatment fee; there is no per-person policy any more.
 */
@Service
public class StaffService {
    private final StaffRepository repository;
    private final AppUserRepository users;
    private final StaffPayAdjustmentService adjustments;

    public StaffService(StaffRepository repository, AppUserRepository users) {
        this(repository, users, null);
    }

    @Autowired
    public StaffService(StaffRepository repository, AppUserRepository users,
                        StaffPayAdjustmentService adjustments) {
        this.repository = repository;
        this.users = users;
        this.adjustments = adjustments;
    }

    @Transactional(readOnly = true)
    public List<Staff> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Staff get(Long id) {
        return find(id);
    }

    /** Next free auto-generated staff code by position, e.g. THP-001, MGR-001. */
    @Transactional(readOnly = true)
    public String nextStaffCode(StaffPosition position) {
        String prefix = position == StaffPosition.Manager ? "MGR-" : "THP-";
        long n = repository.countByStaffCodeStartingWith(prefix) + 1;
        String code;
        do {
            code = String.format("%s%03d", prefix, n++);
        } while (repository.findByStaffCode(code).isPresent());
        return code;
    }

    @Transactional
    public Staff create(String staffCode, String fname, String lname, StaffPosition position,
                         Branch branch, LocalDate hireDate, Long actorId) {
        if (repository.findByStaffCode(normalize(staffCode)).isPresent()) {
            throw new IllegalArgumentException("รหัสพนักงานซ้ำ");
        }
        if (branch == null) {
            throw new IllegalArgumentException("ต้องระบุสาขาต้นสังกัด");
        }
        if (hireDate == null) {
            throw new IllegalArgumentException("ต้องระบุวันที่เริ่มงาน");
        }
        Staff staff = new Staff();
        staff.setStaffCode(normalize(staffCode));
        staff.setStaffFname(requireText(fname, "staffFname"));
        staff.setStaffLname(requireText(lname, "staffLname"));
        staff.setPosition(requirePosition(position));
        staff.setBranch(branch);
        staff.setHireDate(hireDate);
        staff.setStaffStatus(true);
        staff.setStaffCreateAt(Instant.now());
        staff.setStaffCreateBy(actorId);
        return repository.save(staff);
    }

    @Transactional
    public Staff update(Long id, String fname, String lname, StaffPosition position, Branch branch, Long actorId) {
        Staff staff = find(id);
        staff.setStaffFname(requireText(fname, "staffFname"));
        staff.setStaffLname(requireText(lname, "staffLname"));
        staff.setPosition(requirePosition(position));
        if (branch != null) {
            staff.setBranch(branch);
        }
        staff.setStaffUpdateAt(Instant.now());
        staff.setStaffUpdateBy(actorId);
        return repository.save(staff);
    }

    /** Switches a staff member off without a termination record (no commission policy involved). */
    @Transactional
    public Staff deactivate(Long id, Long actorId) {
        Staff staff = find(id);
        staff.setStaffStatus(false);
        staff.setStaffUpdateAt(Instant.now());
        staff.setStaffUpdateBy(actorId);
        return repository.save(staff);
    }

    /** Marks the staff as terminated on the given date (Inactive from then on). */
    @Transactional
    public Staff terminate(Long id, LocalDate terminationDate, Long actorId) {
        Staff staff = find(id);
        if (terminationDate == null) {
            throw new IllegalArgumentException("ต้องระบุวันที่ลาออก");
        }
        if (staff.getHireDate() != null && terminationDate.isBefore(staff.getHireDate())) {
            throw new IllegalArgumentException("วันที่ลาออกต้องไม่ก่อนวันที่เริ่มงาน");
        }
        staff.setStaffStatus(false);
        staff.setTerminationDate(terminationDate);
        staff.setStaffUpdateAt(Instant.now());
        staff.setStaffUpdateBy(actorId);
        var linkedUsers = users.findByStaffId(staff.getId());
        Instant updatedAt = Instant.now();

        for (var user : linkedUsers) {
            user.setUserStatus(false);
            user.setUserUpdateAt(updatedAt);
            user.setUserUpdateBy(actorId);
        }
        users.saveAll(linkedUsers);
        Staff saved = repository.save(staff);
        if (adjustments != null) {
            adjustments.cancelOnTermination(saved.getId(), actorId);
        }
        return saved;
    }

    private Staff find(Long id) {
        return repository.findById(id).orElseThrow(StaffNotFoundException::new);
    }

    private static StaffPosition requirePosition(StaffPosition position) {
        if (position == null) {
            throw new IllegalArgumentException("ต้องระบุตำแหน่ง");
        }
        return position;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("staffCode ห้ามว่าง");
        }
        return value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
