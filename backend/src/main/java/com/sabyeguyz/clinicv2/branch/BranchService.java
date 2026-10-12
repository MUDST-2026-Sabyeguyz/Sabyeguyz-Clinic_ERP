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
