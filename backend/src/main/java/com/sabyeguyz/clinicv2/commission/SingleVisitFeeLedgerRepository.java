package com.sabyeguyz.clinicv2.commission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SingleVisitFeeLedgerRepository extends JpaRepository<SingleVisitFeeLedger, Long> {
    java.util.List<SingleVisitFeeLedger> findByVisitIdOrderByIdAsc(Long visitId);

    java.util.List<SingleVisitFeeLedger> findByStaffIdAndServiceDateBetweenOrderByIdAsc(Long staffId, java.time.LocalDate from,
                                                                                     java.time.LocalDate to);

    java.util.List<SingleVisitFeeLedger> findByServiceDateBetween(java.time.LocalDate from, java.time.LocalDate to);
}
