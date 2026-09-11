package com.sabyeguyz.clinic.commission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionTierRepository extends JpaRepository<CommissionTier, Long> {
    List<CommissionTier> findBySchemeIdAndActiveTrueOrderByTierOrder(Long schemeId);
}
