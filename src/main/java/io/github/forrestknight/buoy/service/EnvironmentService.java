package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.domain.Flag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.persistence.EnvironmentRepository;
import io.github.forrestknight.buoy.persistence.FlagConfigRepository;
import io.github.forrestknight.buoy.persistence.FlagRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
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

    public EnvironmentService(ProjectRepository projectRepository,
                              EnvironmentRepository environmentRepository,
                              FlagRepository flagRepository,
                              FlagConfigRepository flagConfigRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.flagConfigRepository = flagConfigRepository;
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
        environment.setName(name);
        return environment;
    }

    public void delete(String projectKey, String key) {
        environmentRepository.delete(get(projectKey, key));
    }

    private Project project(String projectKey) {
        return projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
    }
}
