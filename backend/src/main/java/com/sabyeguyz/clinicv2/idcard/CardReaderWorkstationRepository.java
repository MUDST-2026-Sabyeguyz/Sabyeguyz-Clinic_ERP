package com.sabyeguyz.clinicv2.idcard;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardReaderWorkstationRepository extends JpaRepository<CardReaderWorkstation, Long> {
    Optional<CardReaderWorkstation> findByTokenHash(String tokenHash);

    Optional<CardReaderWorkstation> findByPairingCodeHash(String pairingCodeHash);

    List<CardReaderWorkstation> findByBranchIdOrderByNameAsc(Long branchId);

    List<CardReaderWorkstation> findAllByOrderByNameAsc();
}
