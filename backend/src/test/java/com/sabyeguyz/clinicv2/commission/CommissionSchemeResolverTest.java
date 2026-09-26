package com.sabyeguyz.clinicv2.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionSchemeResolverTest {
    @Mock CommissionSchemeRepository repository;

    private static CommissionScheme scheme(LocalDate from, LocalDate to) {
        CommissionScheme scheme = new CommissionScheme();
        scheme.setEffectiveFrom(from);
        scheme.setEffectiveTo(to);
        return scheme;
    }

    @Test
    void picksTheSchemeWhoseRangeCoversTheDate() {
        CommissionScheme v1 = scheme(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        when(repository.findByOrderByEffectiveFromDesc()).thenReturn(List.of(v1));

        CommissionScheme resolved = new CommissionSchemeResolver(repository).resolveEffective(LocalDate.of(2026, 8, 18));

        assertThat(resolved).isSameAs(v1);
    }

    @Test
    void newestVersionWinsWhenRangesOverlapAtTheBoundary() {
        CommissionScheme v1 = scheme(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        CommissionScheme v2 = scheme(LocalDate.of(2027, 1, 1), null);
        when(repository.findByOrderByEffectiveFromDesc()).thenReturn(List.of(v2, v1));

        CommissionScheme resolved = new CommissionSchemeResolver(repository).resolveEffective(LocalDate.of(2027, 6, 1));

        assertThat(resolved).isSameAs(v2);
    }

    @Test
    void throwsWhenNoSchemeCoversTheDate() {
        when(repository.findByOrderByEffectiveFromDesc()).thenReturn(List.of());
        assertThatThrownBy(() -> new CommissionSchemeResolver(repository).resolveEffective(LocalDate.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
