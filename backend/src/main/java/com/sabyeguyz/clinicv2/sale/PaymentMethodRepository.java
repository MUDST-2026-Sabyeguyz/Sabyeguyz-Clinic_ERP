package com.sabyeguyz.clinicv2.sale;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
}
