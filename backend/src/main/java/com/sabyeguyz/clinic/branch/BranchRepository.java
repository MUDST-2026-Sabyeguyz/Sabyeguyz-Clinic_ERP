package com.sabyeguyz.clinic.branch;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByCodeAndActiveTrue(String code);
}
