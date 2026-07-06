package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.ActorType;
import io.github.forrestknight.buoy.domain.AuditAction;
import io.github.forrestknight.buoy.domain.AuditLogEntry;
import io.github.forrestknight.buoy.service.AuditQueryService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final ObjectMapper objectMapper;

    public AuditController(AuditQueryService auditQueryService, ObjectMapper objectMapper) {
        this.auditQueryService = auditQueryService;
        this.objectMapper = objectMapper;
    }

    public record AuditEntryResponse(Long id, Instant occurredAt, ActorType actorType, String actorName,
                                     AuditAction action, String entityType, Long entityId, String entityKey,
                                     Long environmentId, JsonNode diff, String source) {
    }

    public record AuditPageResponse(List<AuditEntryResponse> entries, int page, int size,
                                    long totalElements, int totalPages) {
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public AuditPageResponse query(@PathVariable String projectKey,
                                   @RequestParam(required = false) String entityType,
                                   @RequestParam(required = false) AuditAction action,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        Page<AuditLogEntry> result = auditQueryService.query(projectKey, entityType, action, page, size);
        return new AuditPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private AuditEntryResponse toResponse(AuditLogEntry entry) {
        JsonNode diff = entry.getDiff() == null ? null : objectMapper.readTree(entry.getDiff());
        return new AuditEntryResponse(entry.getId(), entry.getOccurredAt(), entry.getActorType(),
                entry.getActorName(), entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                entry.getEntityKey(), entry.getEnvironmentId(), diff, entry.getSource());
    }
}
