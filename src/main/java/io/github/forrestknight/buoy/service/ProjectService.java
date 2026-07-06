package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.ProjectMember;
import io.github.forrestknight.buoy.domain.ProjectRole;
import io.github.forrestknight.buoy.persistence.AppUserRepository;
import io.github.forrestknight.buoy.persistence.ProjectMemberRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AppUserRepository userRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectService(ProjectRepository projectRepository,
                          AppUserRepository userRepository,
                          ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    /** The creating user (when there is one) becomes the project's first OWNER. */
    public Project create(String key, String name, String description, String creatorUsername) {
        if (projectRepository.existsByKey(key)) {
            throw new DuplicateKeyException("Project", key);
        }
        Project project = projectRepository.save(new Project(key, name, description));
        if (creatorUsername != null) {
            userRepository.findByUsername(creatorUsername).ifPresent(user ->
                    memberRepository.save(new ProjectMember(project, user, ProjectRole.OWNER)));
        }
        return project;
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
