package com.sabyeguyz.clinicv2.staff;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.CommissionAfterTerminationPolicy;
import com.sabyeguyz.clinicv2.enums.StaffPosition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff/Physiotherapist management (Clinic ERP Workflow Brief §2).
 * Termination policy follows Commission_System_Requirement_Dev §21: staff
 * keep releasing outstanding commission from courses sold before they left
 * unless the branch's policy is FORFEIT.
 */
@Service
public class StaffService {
    private final StaffRepository repository;

    public StaffService(StaffRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Staff> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Staff get(Long id) {
        return find(id);
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

    /**
     * Marks the staff as terminated. commissionAfterTerminationPolicy decides whether
     * their outstanding commission pool keeps releasing (CONTINUE_UNTIL_COMPLETE) or is
     * cut off (FORFEIT) — see Commission_System_Requirement_Dev §21.
     */
    @Transactional
    public Staff terminate(Long id, LocalDate terminationDate, CommissionAfterTerminationPolicy policy, Long actorId) {
        Staff staff = find(id);
        if (terminationDate == null) {
            throw new IllegalArgumentException("ต้องระบุวันที่ลาออก");
        }
        if (policy == null) {
            throw new IllegalArgumentException("ต้องระบุ commissionAfterTerminationPolicy");
        }
        if (staff.getTerminationPolicyLockedAt() != null && staff.getCommissionAfterTerminationPolicy() != policy) {
            throw new IllegalArgumentException("นโยบายคอมมิชชันหลังออกจากงานถูกล็อกแล้ว ไม่สามารถเปลี่ยนได้");
        }
        staff.setStaffStatus(false);
        staff.setTerminationDate(terminationDate);
        staff.setCommissionAfterTerminationPolicy(policy);
        if (staff.getTerminationPolicyLockedAt() == null) {
            staff.setTerminationPolicyLockedAt(Instant.now());
        }
        staff.setStaffUpdateAt(Instant.now());
        staff.setStaffUpdateBy(actorId);
        return repository.save(staff);
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
