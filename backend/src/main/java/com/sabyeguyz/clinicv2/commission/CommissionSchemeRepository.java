package com.sabyeguyz.clinicv2.commission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionSchemeRepository extends JpaRepository<CommissionScheme, Long> {
    List<CommissionScheme> findByOrderByEffectiveFromDesc();
}
