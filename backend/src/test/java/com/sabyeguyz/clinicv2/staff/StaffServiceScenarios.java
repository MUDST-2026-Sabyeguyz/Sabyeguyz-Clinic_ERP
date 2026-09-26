package com.sabyeguyz.clinicv2.staff;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.CommissionAfterTerminationPolicy;
import com.sabyeguyz.clinicv2.enums.StaffPosition;
import java.time.LocalDate;
import java.util.Optional;

public class StaffServiceScenarios {
    V2RepositoryStub<StaffRepository> repositoryStub = new V2RepositoryStub<>(StaffRepository.class);
    StaffRepository repository = repositoryStub.proxy();

    public Map<String, Object> createsStaffAssignedToABranch() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByStaffCode", args -> Optional.empty());

        Branch branch = new Branch();

        Staff staff = new StaffService(repository).create(
                "THP-001", "สมชาย", "ใจดี", StaffPosition.Therapist, branch, LocalDate.of(2026, 1, 1), 1L);

        out.put("staffCode", staff.getStaffCode());
        out.put("staffStatus", staff.isStaffStatus());
        out.put("assignedBranchIsRequestedBranch", staff.getBranch() == branch);
        return out;
    }

    public Map<String, Object> rejectsDuplicateStaffCode() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByStaffCode", args -> Optional.of(new Staff()));
        out.putAll(V2RepositoryStub.outcome(() -> { new StaffService(repository).create(
                "THP-001", "สมชาย", "ใจดี", StaffPosition.Therapist, new Branch(), LocalDate.now(), 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsMissingBranch() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByStaffCode", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> { new StaffService(repository).create(
                "THP-002", "สมหญิง", "ใจดี", StaffPosition.Therapist, null, LocalDate.now(), 1L); return null; }));
        return out;
    }

    public Map<String, Object> terminateSetsStatusAndPolicy() {
        Map<String, Object> out = new LinkedHashMap<>();
        Staff staff = new Staff();
        staff.setStaffStatus(true);
        repositoryStub.on("findById", args -> Optional.of(staff));

        Staff terminated = new StaffService(repository).terminate(
                5L, LocalDate.of(2026, 6, 30), CommissionAfterTerminationPolicy.CONTINUE_UNTIL_COMPLETE, 9L);

        out.put("staffStatus", terminated.isStaffStatus());
        out.put("terminationDate", terminated.getTerminationDate());
        out.put("commissionAfterTerminationPolicy", terminated.getCommissionAfterTerminationPolicy());
        return out;
    }

    public Map<String, Object> terminateRequiresAPolicy() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findById", args -> Optional.of(new Staff()));
        out.putAll(V2RepositoryStub.outcome(() -> { new StaffService(repository).terminate(5L, LocalDate.now(), null, 9L); return null; }));
        return out;
    }
}
