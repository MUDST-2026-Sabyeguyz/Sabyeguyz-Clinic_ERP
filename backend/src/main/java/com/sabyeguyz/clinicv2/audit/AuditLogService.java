package com.sabyeguyz.clinicv2.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.enums.AuditAction;

/**
 * Generic row-level audit trail (Clinic ERP Workflow Brief §9 "System
 * Thinking": every transaction must be traceable back to who/when/what
 * changed). Not yet wired into any of the other v2 services — each domain
 * service currently writes its own purpose-built audit rows (Appointment_
 * status_history, Course_ledger, Commission_allocation_ledger, Commission_
 * rate_override_audit) instead. This is here for changes that don't have
 * one of those dedicated tables.
 */
@Service
public class AuditLogService {
    /** Source written into new_value of an automatic event (no user) that did not name one. */
    public static final String DEFAULT_SOURCE = "System";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Builds the field map of a before/after value: {@code fields("status", "Open", "reason", text)}; nulls are kept. */
    public static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }

        return map;
    }

    /**
     * Data Dictionary Audit_log: old_value and new_value are JSON. A user action carries changed_by; an automatic event
     * has changed_by NULL and names where it came from in new_value ("source", for example GoogleCalendar), and is never
     * attributed to a staff user.
     */
    @Transactional
    public AuditLog recordChange(
            String tableName,
            String recordId,
            AuditAction action,
            Map<String, ?> oldValue,
            Map<String, ?> newValue,
            Long actorId) {
        return record(
                tableName,
                recordId,
                action,
                toJson(oldValue),
                toJson(newValue),
                actorId);
    }

    /** An automatic event: no user, the source is part of the new value. */
    @Transactional
    public AuditLog recordAutomatic(
            String tableName,
            String recordId,
            AuditAction action,
            Map<String, ?> oldValue,
            Map<String, ?> newValue,
            String source) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", source);

        if (newValue != null) value.putAll(newValue);
        return record(
                tableName,
                recordId,
                action,
                toJson(oldValue),
                toJson(value),
                null);
    }

    @Transactional
    public AuditLog record(
            String tableName,
            String recordId,
            AuditAction action,
            String oldValue,
            String newValue,
            Long actorId) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName ห้ามว่าง");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId ห้ามว่าง");
        }
        if (action == null) {
            throw new IllegalArgumentException("ต้องระบุ action");
        }
        AuditLog log = new AuditLog();

        log.setTableName(tableName);
        log.setRecordId(recordId);
        log.setAction(action);
        log.setOldValue(asJson(oldValue));
        log.setNewValue(actorId == null ? withSource(asJson(newValue)) : asJson(newValue));
        log.setChangedBy(actorId);
        log.setChangedAt(Instant.now());

        return repository.save(log);
    }

    private static String toJson(Map<String, ?> value) {
        if (value == null) return null;
        try {
            return JSON.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("audit value is not JSON: " + invalid.getOriginalMessage());
        }
    }

    /** Text that is already a JSON object or array is kept; any other text is wrapped as {"summary": text}. */
    static String asJson(String value) {
        if (value == null) return null;
        String trimmed = value.trim();

        if ((trimmed.startsWith("{")
                && trimmed.endsWith("}"))
                || (trimmed.startsWith("[")
                && trimmed.endsWith("]"))) {
            try {
                JSON.readTree(trimmed);
                return trimmed;
            } catch (com.fasterxml.jackson.core.JsonProcessingException notJson) {
                // fall through: wrapped below
            }
        }
        return toJson(fields("summary", value));
    }

    /** An event without a user must say where it came from. */
    private static String withSource(String json) {
        try {
            JsonNode node = json == null ? JSON.createObjectNode() : JSON.readTree(json);

            if (!node.isObject()) {
                node = JSON.createObjectNode().set("value", node);
            }

            ObjectNode object = (ObjectNode) node;

            if (!object.hasNonNull("source")) {
                object.put("source", DEFAULT_SOURCE);
            }

            return JSON.writeValueAsString(object);

        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("audit value is not JSON: " + invalid.getOriginalMessage());
        }
    }
}
