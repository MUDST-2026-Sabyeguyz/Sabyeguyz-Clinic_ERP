package com.sabyeguyz.clinic.masterdata; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface MasterDataRepository extends JpaRepository<MasterData,Long>{List<MasterData> findAllByOrderByCategoryAscNameAsc();Optional<MasterData> findByCategoryAndCode(String category,String code);}
