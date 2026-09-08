package com.sabyeguyz.clinic.commission;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionSchemeRepository extends JpaRepository<CommissionScheme, Long> {
    List<CommissionScheme> findByOrderByEffectiveFromDesc();
}
