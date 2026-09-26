package com.sabyeguyz.clinicv2.masterdata;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterDataValueRepository extends JpaRepository<MasterDataValue, Long> {
    List<MasterDataValue> findByTypeIdOrderBySortOrder(Long typeId);
}
