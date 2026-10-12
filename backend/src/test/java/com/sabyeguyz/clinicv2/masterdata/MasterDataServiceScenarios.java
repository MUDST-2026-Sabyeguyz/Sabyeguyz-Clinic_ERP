package com.sabyeguyz.clinicv2.masterdata;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import java.util.Optional;

public class MasterDataServiceScenarios {
    V2RepositoryStub<MasterDataTypeRepository> typesStub = new V2RepositoryStub<>(MasterDataTypeRepository.class);
    MasterDataTypeRepository types = typesStub.proxy();
    V2RepositoryStub<MasterDataValueRepository> valuesStub = new V2RepositoryStub<>(MasterDataValueRepository.class);
    MasterDataValueRepository values = valuesStub.proxy();

    private MasterDataService service() {
        return new MasterDataService(types, values);
    }

    public Map<String, Object> createsAType() {
        Map<String, Object> out = new LinkedHashMap<>();
        typesStub.on("findByTypeName", args -> Optional.empty());

        MasterDataType type = service().createType("CUSTOMER_GROUP", 1L);

        out.put("typeName", type.getTypeName());
        return out;
    }

    public Map<String, Object> rejectsDuplicateTypeName() {
        Map<String, Object> out = new LinkedHashMap<>();
        typesStub.on("findByTypeName", args -> Optional.of(new MasterDataType()));
        out.putAll(V2RepositoryStub.outcome(() -> { service().createType("CUSTOMER_GROUP", 1L); return null; }));
        return out;
    }

    public Map<String, Object> createsAValueUnderAType() {
        Map<String, Object> out = new LinkedHashMap<>();
        MasterDataType type = new MasterDataType();

        MasterDataValue value = service().createValue(type, "VIP", 1, 1L);

        out.put("valueName", value.getValueName());
        out.put("valueStatus", value.isValueStatus());
        return out;
    }

    public Map<String, Object> deactivateThrowsWhenValueMissing() {
        Map<String, Object> out = new LinkedHashMap<>();
        valuesStub.on("findById", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> { service().deactivateValue(99L, 1L); return null; }));
        return out;
    }
}
