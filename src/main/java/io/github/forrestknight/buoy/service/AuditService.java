package io.github.forrestknight.buoy.service;

import org.jspecify.annotations.Nullable;
import io.github.forrestknight.buoy.config.ApiKeyAuthentication;
import io.github.forrestknight.buoy.domain.ActorType;
import io.github.forrestknight.buoy.domain.AuditAction;
import io.github.forrestknight.buoy.domain.AuditLogEntry;
import io.github.forrestknight.buoy.persistence.AuditLogEntryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes one immutable {@link AuditLogEntry} per mutation, in the same transaction
 * as the mutation itself. Actor and request source are resolved from the current
 * security and request contexts; background work (bootstrap, seeding) records as SYSTEM.
 */
@Service
public class AuditService {

    private final AuditLogEntryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param before snapshot before the change ({@code null} for CREATED)
     * @param after  snapshot after the change ({@code null} for DELETED)
     */
    public void record(AuditAction action, String entityType, Long entityId, @Nullable String entityKey,
                       @Nullable Long projectId, @Nullable Long environmentId,
                       @Nullable Object before, @Nullable Object after) {
        Actor actor = currentActor();
        repository.save(new AuditLogEntry(actor.type(), actor.name(), action, entityType,
                entityId, entityKey, projectId, environmentId, diff(before, after), currentSource()));
    }

    private record Actor(ActorType type, String name) {
    }

    private String diff(@Nullable Object before, @Nullable Object after) {
        ObjectNode diff = objectMapper.createObjectNode();
        if (before == null) {
            diff.putNull("before");
        } else {
            diff.set("before", objectMapper.valueToTree(before));
        }
        if (after == null) {
            diff.putNull("after");
        } else {
            diff.set("after", objectMapper.valueToTree(after));
        }
        return diff.toString();
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return new Actor(ActorType.SYSTEM, "system");
        }
        if (authentication instanceof ApiKeyAuthentication apiKey) {
            return new Actor(ActorType.API_KEY, apiKey.getPrincipal().name());
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return new Actor(ActorType.USER, jwt.getSubject());
        }
        return new Actor(ActorType.SYSTEM, "system");
    }

    private String currentSource() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "system";
    }
}
