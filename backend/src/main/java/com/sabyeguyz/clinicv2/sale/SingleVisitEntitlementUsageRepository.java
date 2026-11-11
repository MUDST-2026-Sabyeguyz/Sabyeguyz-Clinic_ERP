package com.sabyeguyz.clinicv2.sale;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SingleVisitEntitlementUsageRepository extends JpaRepository<SingleVisitEntitlementUsage, Long> {
    long countByEntitlementIdAndReversedAtIsNull(Long entitlementId);
    boolean existsByEntitlementIdAndAppointmentId(Long entitlementId, Long appointmentId);
    List<SingleVisitEntitlementUsage> findByEntitlementIdOrderByUsedAtAsc(Long entitlementId);
}
