package com.sabyeguyz.clinic.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findAllByOrderByName();

    List<PaymentMethod> findByActiveTrueOrderByName();

    Optional<PaymentMethod> findByCode(String code);
}
