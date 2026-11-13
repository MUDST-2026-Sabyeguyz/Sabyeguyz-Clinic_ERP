package com.sabyeguyz.clinicv2.sale;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    /** v2 has no payment_method code column - lookups match by method_name instead. */
    @org.springframework.data.jpa.repository.Query(value =
            "select * from payment_method where lower(method_name::text) = lower(:name)", nativeQuery = true)
    Optional<PaymentMethod> findByMethodNameIgnoreCase(
            @org.springframework.data.repository.query.Param("name") String methodName);
}
