package com.sabyeguyz.clinic.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicResourceRepository extends JpaRepository<ClinicResource, Long> {
    List<ClinicResource> findAllByOrderByName();

    Optional<ClinicResource> findByCode(String code);
}
