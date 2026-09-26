package com.sabyeguyz.clinicv2.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.enums.ServiceType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {
    @Mock ServiceRepository repository;

    @Test
    void createsASingleVisitService() {
        when(repository.findByServiceCode("PHYSIO_30")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Service service = new ServiceCatalogService(repository).create(
                "PHYSIO_30", "กายภาพบำบัด 30 นาที", ServiceType.SingleVisit, new BigDecimal("700.00"),
                null, null, null, 1L);

        assertThat(service.getServiceCode()).isEqualTo("PHYSIO_30");
        assertThat(service.isServiceStatus()).isTrue();
    }

    @Test
    void courseRequiresADefaultTotalVisits() {
        when(repository.findByServiceCode("COURSE_10")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new ServiceCatalogService(repository).create(
                "COURSE_10", "คอร์ส 10 ครั้ง", ServiceType.Course, new BigDecimal("10000.00"),
                null, null, 90, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePrice() {
        when(repository.findByServiceCode("X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new ServiceCatalogService(repository).create(
                "X", "ชื่อ", ServiceType.Assessment, new BigDecimal("-1"), null, null, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
