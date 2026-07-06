package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project create(String key, String name, String description) {
        if (projectRepository.existsByKey(key)) {
            throw new DuplicateKeyException("Project", key);
        }
        return projectRepository.save(new Project(key, name, description));
    }

    @Transactional(readOnly = true)
    public Project get(String key) {
        return projectRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("Project", key));
    }

    @Transactional(readOnly = true)
    public List<Project> list() {
        return projectRepository.findAll();
    }

    public Project update(String key, String name, String description) {
        Project project = get(key);
        project.setName(name);
        project.setDescription(description);
        return project;
    }

    public void delete(String key) {
        projectRepository.delete(get(key));
    }
}
