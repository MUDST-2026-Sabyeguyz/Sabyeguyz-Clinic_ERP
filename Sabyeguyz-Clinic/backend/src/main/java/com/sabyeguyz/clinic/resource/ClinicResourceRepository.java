package com.sabyeguyz.clinic.resource; import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface ClinicResourceRepository extends JpaRepository<ClinicResource,Long>{List<ClinicResource> findAllByOrderByName();Optional<ClinicResource> findByCode(String code);}
