package com.scansettle.api.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.common.web.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The one place application code writes an {@link AuditEvent} — every
 * payment-affecting mutation goes through this (docs/security.md, Section 8 of the
 * brief). Serialization failures are logged, never thrown: a broken audit payload
 * must not roll back the business transaction it's describing.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID merchantId, AuditEvent.ActorType actorType, String actorId, String action,
                        String entityType, String entityId, Object before, Object after) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), merchantId, actorType, actorId, action, entityType, entityId,
                toJson(before), toJson(after), CorrelationId.currentOrUnknown());
        auditEventRepository.save(event);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize audit payload for {}", value.getClass(), e);
            return null;
        }
    }
}
