package io.github.forrestknight.buoy.service;

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

    public SegmentService(ProjectRepository projectRepository,
                          SegmentRepository segmentRepository,
                          TargetingRuleValidator ruleValidator) {
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
        this.ruleValidator = ruleValidator;
    }

    public Segment create(String projectKey, String key, String name, String description, List<Clause> clauses) {
        Project project = project(projectKey);
        if (segmentRepository.existsByProjectIdAndKey(project.getId(), key)) {
            throw new DuplicateKeyException("Segment", key);
        }
        ruleValidator.validateSegmentClauses(clauses);
        return segmentRepository.save(new Segment(project, key, name, description, clauses));
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
        segment.setName(name);
        segment.setDescription(description);
        segment.setClauses(clauses);
        return segment;
    }

    public void delete(String projectKey, String key) {
        segmentRepository.delete(get(projectKey, key));
    }

    private Project project(String projectKey) {
        return projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
    }
}
