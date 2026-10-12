package com.sabyeguyz.clinicv2.branch;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByBranchCode(String branchCode);
    Optional<Branch> findByBranchCodeAndBranchStatusTrue(String branchCode);
}
