package com.sabyeguyz.clinicv2.masterdata;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterDataTypeRepository extends JpaRepository<MasterDataType, Long> {
    Optional<MasterDataType> findByTypeName(String typeName);
}
