package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.domain.Flag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.TargetingRule;
import io.github.forrestknight.buoy.persistence.EnvironmentRepository;
import io.github.forrestknight.buoy.persistence.FlagConfigRepository;
import io.github.forrestknight.buoy.persistence.FlagRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FlagService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagRepository flagRepository;
    private final FlagConfigRepository flagConfigRepository;
    private final TargetingRuleValidator ruleValidator;

    public FlagService(ProjectRepository projectRepository,
                       EnvironmentRepository environmentRepository,
                       FlagRepository flagRepository,
                       FlagConfigRepository flagConfigRepository,
                       TargetingRuleValidator ruleValidator) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.flagConfigRepository = flagConfigRepository;
        this.ruleValidator = ruleValidator;
    }

    /**
     * Creating a flag auto-creates a disabled config in every environment of the
     * project, so evaluation never encounters a flag without a config (brief §4.1).
     */
    public Flag create(String projectKey, String key, String name, String description, List<String> tags) {
        Project project = project(projectKey);
        if (flagRepository.existsByProjectIdAndKey(project.getId(), key)) {
            throw new DuplicateKeyException("Flag", key);
        }
        Flag flag = flagRepository.save(new Flag(project, key, name, description, tags));
        for (Environment environment : environmentRepository.findByProjectId(project.getId())) {
            flagConfigRepository.save(new FlagConfig(flag, environment));
        }
        return flag;
    }

    @Transactional(readOnly = true)
    public Flag get(String projectKey, String key) {
        return flagRepository.findByProjectIdAndKey(project(projectKey).getId(), key)
                .orElseThrow(() -> new NotFoundException("Flag", key));
    }

    @Transactional(readOnly = true)
    public List<Flag> list(String projectKey) {
        return flagRepository.findByProjectId(project(projectKey).getId());
    }

    public Flag update(String projectKey, String key, String name, String description,
                       List<String> tags, boolean archived) {
        Flag flag = get(projectKey, key);
        flag.setName(name);
        flag.setDescription(description);
        flag.setTags(tags);
        flag.setArchived(archived);
        return flag;
    }

    public void delete(String projectKey, String key) {
        flagRepository.delete(get(projectKey, key));
    }

    @Transactional(readOnly = true)
    public FlagConfig getConfig(String projectKey, String flagKey, String environmentKey) {
        Flag flag = get(projectKey, flagKey);
        Environment environment = environment(flag.getProject(), environmentKey);
        return flagConfigRepository.findByFlagIdAndEnvironmentId(flag.getId(), environment.getId())
                .orElseThrow(() -> new NotFoundException("Flag config", flagKey + "/" + environmentKey));
    }

    /**
     * Full-replace config update guarded by optimistic locking: the request carries the
     * version it was based on; a mismatch is a 409 before any field is touched. The
     * {@code @Version} column still guards true concurrent transactions.
     */
    public FlagConfig updateConfig(String projectKey, String flagKey, String environmentKey,
                                   long expectedVersion, boolean enabled, List<TargetingRule> rules,
                                   boolean defaultVariation, boolean offVariation) {
        FlagConfig config = getConfig(projectKey, flagKey, environmentKey);
        if (config.getVersion() != expectedVersion) {
            throw new StaleVersionException(expectedVersion, config.getVersion());
        }
        List<TargetingRule> validated = ruleValidator.validateAndAssignIds(
                config.getFlag().getProject().getId(), rules);
        config.setEnabled(enabled);
        config.setRules(validated);
        config.setDefaultVariation(defaultVariation);
        config.setOffVariation(offVariation);
        return config;
    }

    private Project project(String projectKey) {
        return projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
    }

    private Environment environment(Project project, String environmentKey) {
        return environmentRepository.findByProjectIdAndKey(project.getId(), environmentKey)
                .orElseThrow(() -> new NotFoundException("Environment", environmentKey));
    }
}
