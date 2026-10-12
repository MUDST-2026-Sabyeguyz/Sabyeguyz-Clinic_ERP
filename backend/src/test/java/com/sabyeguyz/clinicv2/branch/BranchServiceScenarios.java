package com.sabyeguyz.clinicv2.branch;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import java.util.Optional;

public class BranchServiceScenarios {
    V2RepositoryStub<BranchRepository> repositoryStub = new V2RepositoryStub<>(BranchRepository.class);
    BranchRepository repository = repositoryStub.proxy();

    public Map<String, Object> createsBranchWithValidTwoDigitCode() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByBranchCode", args -> Optional.empty());

        Branch branch = new BranchService(repository).create("01", "สาขาราชพฤกษ์", "ที่อยู่", "0800000000", 1L);

        out.put("branchCode", branch.getBranchCode());
        out.put("branchStatus", branch.isBranchStatus());
        out.put("branchCreateBy", branch.getBranchCreateBy());
        return out;
    }

    public Map<String, Object> rejectsNonNumericBranchCode() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(V2RepositoryStub.outcome(() -> { new BranchService(repository).create("RP", "ชื่อ", "ที่อยู่", "080", 1L); return null; }));
        out.put("repositorySaveCalls", repositoryStub.calls("save"));
        return out;
    }

    public Map<String, Object> rejectsDuplicateBranchCode() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByBranchCode", args -> Optional.of(new Branch()));
        out.putAll(V2RepositoryStub.outcome(() -> { new BranchService(repository).create("01", "ชื่อ", "ที่อยู่", "080", 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsBlankAddress() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByBranchCode", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> { new BranchService(repository).create("03", "ชื่อ", "  ", "080", 1L); return null; }));
        return out;
    }

    public Map<String, Object> updateNeverTouchesBranchCode() {
        Map<String, Object> out = new LinkedHashMap<>();
        Branch existing = new Branch();
        existing.setBranchCode("02");
        repositoryStub.on("findById", args -> Optional.of(existing));

        Branch updated = new BranchService(repository).update(10L, "ชื่อใหม่", "ที่อยู่ใหม่", "081", 2L);

        out.put("branchCode", updated.getBranchCode());
        out.put("branchName", updated.getBranchName());
        out.put("branchUpdateBy", updated.getBranchUpdateBy());
        return out;
    }

    public Map<String, Object> deactivateThrowsWhenBranchMissing() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findById", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> { new BranchService(repository).deactivate(999L, 1L); return null; }));
        return out;
    }
}
