package com.sabyeguyz.clinicv2.sale;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    /** v2 has no payment_method code column - lookups match by method_name instead. */
    Optional<PaymentMethod> findByMethodNameIgnoreCase(String methodName);
}
