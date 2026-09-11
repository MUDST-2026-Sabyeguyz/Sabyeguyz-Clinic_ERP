package com.sabyeguyz.clinic.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    Optional<ServiceItem> findByCode(String code);
    List<ServiceItem> findByActiveTrueOrderByName();
}
