package com.sabyeguyz.clinicv2.commission;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommissionSchemeRepository extends JpaRepository<CommissionScheme, Long> {
    List<CommissionScheme> findByOrderByEffectiveFromDesc();

    /** Serializes scheme version allocation until the transaction ends. */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock("
            + "hashtext('commission-scheme'), "
            + "hashtext('version-allocation'))",
            nativeQuery = true)
    Integer lockVersionAllocation();
}
