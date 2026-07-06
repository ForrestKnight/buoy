package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AuditAction;
import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.Segment;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import io.github.forrestknight.buoy.persistence.SegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SegmentService {

    private final ProjectRepository projectRepository;
    private final SegmentRepository segmentRepository;
    private final TargetingRuleValidator ruleValidator;
    private final AuditService auditService;

    public SegmentService(ProjectRepository projectRepository,
                          SegmentRepository segmentRepository,
                          TargetingRuleValidator ruleValidator,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
        this.ruleValidator = ruleValidator;
        this.auditService = auditService;
    }

    public Segment create(String projectKey, String key, String name, String description, List<Clause> clauses) {
        Project project = project(projectKey);
        if (segmentRepository.existsByProjectIdAndKey(project.getId(), key)) {
            throw new DuplicateKeyException("Segment", key);
        }
        ruleValidator.validateSegmentClauses(clauses);
        Segment segment = segmentRepository.save(new Segment(project, key, name, description, clauses));
        auditService.record(AuditAction.CREATED, "SEGMENT", segment.getId(), segment.getKey(),
                project.getId(), null, null, AuditSnapshots.of(segment));
        return segment;
    }

    @Transactional(readOnly = true)
    public Segment get(String projectKey, String key) {
        return segmentRepository.findByProjectIdAndKey(project(projectKey).getId(), key)
                .orElseThrow(() -> new NotFoundException("Segment", key));
    }

    @Transactional(readOnly = true)
    public List<Segment> list(String projectKey) {
        return segmentRepository.findByProjectId(project(projectKey).getId());
    }

    public Segment update(String projectKey, String key, String name, String description, List<Clause> clauses) {
        Segment segment = get(projectKey, key);
        ruleValidator.validateSegmentClauses(clauses);
        var before = AuditSnapshots.of(segment);
        segment.setName(name);
        segment.setDescription(description);
        segment.setClauses(clauses);
        auditService.record(AuditAction.UPDATED, "SEGMENT", segment.getId(), segment.getKey(),
                segment.getProject().getId(), null, before, AuditSnapshots.of(segment));
        return segment;
    }

    public void delete(String projectKey, String key) {
        Segment segment = get(projectKey, key);
        auditService.record(AuditAction.DELETED, "SEGMENT", segment.getId(), segment.getKey(),
                segment.getProject().getId(), null, AuditSnapshots.of(segment), null);
        segmentRepository.delete(segment);
    }

    private Project project(String projectKey) {
        return projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
    }
}
