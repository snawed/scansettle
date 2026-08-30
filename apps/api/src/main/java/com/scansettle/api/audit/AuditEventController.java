package com.scansettle.api.audit;

import com.scansettle.api.security.CurrentPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class AuditEventController {

    private final AuditEventRepository auditEventRepository;
    private final CurrentPrincipal currentPrincipal;

    public AuditEventController(AuditEventRepository auditEventRepository, CurrentPrincipal currentPrincipal) {
        this.auditEventRepository = auditEventRepository;
        this.currentPrincipal = currentPrincipal;
    }

    public record AuditEventResponse(String id, String actorType, String actorId, String action, String entityType,
                                      String entityId, Instant occurredAt) {
    }

    @GetMapping("/api/v1/audit-events")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEventResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        Page<AuditEvent> results = auditEventRepository.findByMerchantIdOrderByOccurredAtDesc(
                currentPrincipal.merchantId(), PageRequest.of(page, size));
        return results.map(this::toResponse).toList();
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getId().toString(), event.getActorType().name(), event.getActorId(),
                event.getAction(), event.getEntityType(), event.getEntityId(), event.getOccurredAt());
    }
}
