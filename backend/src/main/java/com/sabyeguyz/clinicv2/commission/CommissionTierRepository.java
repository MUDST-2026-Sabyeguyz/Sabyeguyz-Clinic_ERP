package com.sabyeguyz.clinicv2.commission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionTierRepository extends JpaRepository<CommissionTier, Long> {
    List<CommissionTier> findByCommissionSchemeIdOrderByTierOrder(Long commissionSchemeId);
}
