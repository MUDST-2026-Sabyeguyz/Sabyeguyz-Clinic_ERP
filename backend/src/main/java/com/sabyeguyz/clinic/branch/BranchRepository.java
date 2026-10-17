package com.sabyeguyz.clinic.branch;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByBranchCodeAndActiveTrue(String branchCode);
    List<Branch> findAllByOrderByName();
    Optional<Branch> findByBranchCode(String branchCode);
}
