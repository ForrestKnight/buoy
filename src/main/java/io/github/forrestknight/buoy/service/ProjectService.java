package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AuditAction;
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
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository,
                          AppUserRepository userRepository,
                          ProjectMemberRepository memberRepository,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
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
        auditService.record(AuditAction.CREATED, "PROJECT", project.getId(), project.getKey(),
                project.getId(), null, null, AuditSnapshots.of(project));
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
        var before = AuditSnapshots.of(project);
        project.setName(name);
        project.setDescription(description);
        auditService.record(AuditAction.UPDATED, "PROJECT", project.getId(), project.getKey(),
                project.getId(), null, before, AuditSnapshots.of(project));
        return project;
    }

    public void delete(String key) {
        Project project = get(key);
        auditService.record(AuditAction.DELETED, "PROJECT", project.getId(), project.getKey(),
                project.getId(), null, AuditSnapshots.of(project), null);
        projectRepository.delete(project);
    }
}
