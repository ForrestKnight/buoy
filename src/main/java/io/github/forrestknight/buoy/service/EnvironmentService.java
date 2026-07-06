package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AuditAction;
import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.domain.Flag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.persistence.EnvironmentRepository;
import io.github.forrestknight.buoy.persistence.FlagConfigRepository;
import io.github.forrestknight.buoy.persistence.FlagRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EnvironmentService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagRepository flagRepository;
    private final FlagConfigRepository flagConfigRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public EnvironmentService(ProjectRepository projectRepository,
                              EnvironmentRepository environmentRepository,
                              FlagRepository flagRepository,
                              FlagConfigRepository flagConfigRepository,
                              AuditService auditService,
                              ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.flagConfigRepository = flagConfigRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creating an environment backfills a disabled config for every existing flag,
     * so evaluation never encounters a flag without a config (brief §4.1).
     */
    public Environment create(String projectKey, String key, String name) {
        Project project = project(projectKey);
        if (environmentRepository.existsByProjectIdAndKey(project.getId(), key)) {
            throw new DuplicateKeyException("Environment", key);
        }
        Environment environment = environmentRepository.save(new Environment(project, key, name));
        for (Flag flag : flagRepository.findByProjectId(project.getId())) {
            flagConfigRepository.save(new FlagConfig(flag, environment));
        }
        auditService.record(AuditAction.CREATED, "ENVIRONMENT", environment.getId(), environment.getKey(),
                project.getId(), environment.getId(), null, AuditSnapshots.of(environment));
        return environment;
    }

    @Transactional(readOnly = true)
    public Environment get(String projectKey, String key) {
        Project project = project(projectKey);
        return environmentRepository.findByProjectIdAndKey(project.getId(), key)
                .orElseThrow(() -> new NotFoundException("Environment", key));
    }

    @Transactional(readOnly = true)
    public List<Environment> list(String projectKey) {
        return environmentRepository.findByProjectId(project(projectKey).getId());
    }

    public Environment update(String projectKey, String key, String name) {
        Environment environment = get(projectKey, key);
        var before = AuditSnapshots.of(environment);
        environment.setName(name);
        auditService.record(AuditAction.UPDATED, "ENVIRONMENT", environment.getId(), environment.getKey(),
                environment.getProject().getId(), environment.getId(), before, AuditSnapshots.of(environment));
        return environment;
    }

    public void delete(String projectKey, String key) {
        Environment environment = get(projectKey, key);
        auditService.record(AuditAction.DELETED, "ENVIRONMENT", environment.getId(), environment.getKey(),
                environment.getProject().getId(), environment.getId(), AuditSnapshots.of(environment), null);
        eventPublisher.publishEvent(new EnvironmentRemovedEvent(environment.getId()));
        environmentRepository.delete(environment);
    }

    private Project project(String projectKey) {
        return projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
    }
}
