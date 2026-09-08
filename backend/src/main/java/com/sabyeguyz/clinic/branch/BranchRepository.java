package com.sabyeguyz.clinic.branch;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByCodeAndActiveTrue(String code);
    List<Branch> findAllByOrderByName();
    Optional<Branch> findByCode(String code);
    Optional<Branch> findByNumericCode(String numericCode);
}
