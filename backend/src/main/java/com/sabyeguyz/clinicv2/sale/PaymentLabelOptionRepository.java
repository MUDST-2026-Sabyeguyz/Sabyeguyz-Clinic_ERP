package com.sabyeguyz.clinicv2.sale;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLabelOptionRepository
        extends JpaRepository<PaymentLabelOption, Long> {

    Optional<PaymentLabelOption> findByCode(String code);
    List<PaymentLabelOption> findAllByOrderByIdAsc();
}
