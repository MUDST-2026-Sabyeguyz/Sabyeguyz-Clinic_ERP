package com.sabyeguyz.clinicv2.masterdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CustomerGroup, ReferralChannel, InsuranceCompany and other admin-editable
 * dropdowns (Clinic ERP Workflow Brief §2 "Master Data").
 */
@Service
public class MasterDataService {
    private final MasterDataTypeRepository types;
    private final MasterDataValueRepository values;

    public MasterDataService(
            MasterDataTypeRepository types,
            MasterDataValueRepository values) {
        this.types = types;
        this.values = values;
    }

    @Transactional(readOnly = true)
    public List<MasterDataType> listTypes() {
        return types.findAll();
    }

    @Transactional
    public MasterDataType createType(
            String typeName,
            Long actorId) {
        String name = requireText(typeName, "typeName");

        if (types.findByTypeName(name).isPresent()) {
            throw new IllegalArgumentException("มี Master Data Type นี้อยู่แล้ว");
        }

        MasterDataType type = new MasterDataType();
        type.setTypeName(name);
        type.setTypeCreateAt(Instant.now());
        type.setTypeCreateBy(actorId);

        return types.save(type);
    }

    @Transactional(readOnly = true)
    public List<MasterDataValue> listValues(Long typeId) {
        return values.findByTypeIdOrderBySortOrder(typeId);
    }

    @Transactional
    public MasterDataValue createValue(
            MasterDataType type,
            String valueName,
            Integer sortOrder,
            Long actorId) {
        require(type != null, "ต้องระบุ Master Data Type");

        MasterDataValue value = new MasterDataValue();
        value.setType(type);
        value.setValueName(requireText(valueName, "valueName"));
        value.setValueStatus(true);
        value.setSortOrder(sortOrder);
        value.setValueCreateAt(Instant.now());
        value.setValueCreateBy(actorId);

        return values.save(value);
    }

    @Transactional
    public MasterDataValue deactivateValue(
            Long valueId,
            Long actorId) {
        MasterDataValue value = values.findById(valueId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบ Master Data Value"));

        value.setValueStatus(false);
        value.setValueUpdateAt(Instant.now());
        value.setValueUpdateBy(actorId);

        return values.save(value);
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireText(
            String value,
            String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }

        return value.trim();
    }
}
