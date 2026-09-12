package com.sabyeguyz.clinic.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface MasterDataRepository extends JpaRepository<MasterData, Long> {
    List<MasterData> findAllByOrderByCategoryAscNameAsc();

    Optional<MasterData> findByCategoryAndCode(String category, String code);
}
