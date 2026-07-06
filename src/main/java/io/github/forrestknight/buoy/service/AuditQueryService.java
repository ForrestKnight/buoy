package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AuditAction;
import io.github.forrestknight.buoy.domain.AuditLogEntry;
import io.github.forrestknight.buoy.persistence.AuditLogEntryRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogEntryRepository auditRepository;
    private final ProjectRepository projectRepository;

    public AuditQueryService(AuditLogEntryRepository auditRepository, ProjectRepository projectRepository) {
        this.auditRepository = auditRepository;
        this.projectRepository = projectRepository;
    }

    public Page<AuditLogEntry> query(String projectKey, String entityType, AuditAction action,
                                     int page, int size) {
        Long projectId = projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey))
                .getId();

        Specification<AuditLogEntry> spec =
                (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }

        var pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return auditRepository.findAll(spec, pageable);
    }
}
