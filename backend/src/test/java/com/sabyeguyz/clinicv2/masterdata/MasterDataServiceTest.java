package com.sabyeguyz.clinicv2.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {
    @Mock MasterDataTypeRepository types;
    @Mock MasterDataValueRepository values;

    private MasterDataService service() {
        return new MasterDataService(types, values);
    }

    @Test
    void createsAType() {
        when(types.findByTypeName("CUSTOMER_GROUP")).thenReturn(Optional.empty());
        when(types.save(any())).thenAnswer(i -> i.getArgument(0));

        MasterDataType type = service().createType("CUSTOMER_GROUP", 1L);

        assertThat(type.getTypeName()).isEqualTo("CUSTOMER_GROUP");
    }

    @Test
    void rejectsDuplicateTypeName() {
        when(types.findByTypeName("CUSTOMER_GROUP")).thenReturn(Optional.of(new MasterDataType()));
        assertThatThrownBy(() -> service().createType("CUSTOMER_GROUP", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsAValueUnderAType() {
        MasterDataType type = new MasterDataType();
        when(values.save(any())).thenAnswer(i -> i.getArgument(0));

        MasterDataValue value = service().createValue(type, "VIP", 1, 1L);

        assertThat(value.getValueName()).isEqualTo("VIP");
        assertThat(value.isValueStatus()).isTrue();
    }

    @Test
    void deactivateThrowsWhenValueMissing() {
        when(values.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().deactivateValue(99L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
