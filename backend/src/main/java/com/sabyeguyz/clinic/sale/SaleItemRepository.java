package com.sabyeguyz.clinic.sale;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<SaleItem> findBySaleIdOrderByIdAsc(Long saleId);
}
