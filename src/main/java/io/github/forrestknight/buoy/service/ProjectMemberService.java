package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AppUser;
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
public class ProjectMemberService {

    private final ProjectRepository projectRepository;
    private final AppUserRepository userRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectMemberService(ProjectRepository projectRepository,
                                AppUserRepository userRepository,
                                ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    /** Upsert: adds the user to the project or changes their existing role. */
    public ProjectMember assign(String projectKey, String username, ProjectRole role) {
        Project project = project(projectKey);
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User", username));
        return memberRepository.findByProjectIdAndUserId(project.getId(), user.getId())
                .map(member -> {
                    member.setRole(role);
                    return member;
                })
                .orElseGet(() -> memberRepository.save(new ProjectMember(project, user, role)));
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> list(String projectKey) {
        return memberRepository.findByProjectId(project(projectKey).getId());
    }

    public void remove(String projectKey, String username) {
        Project project = project(projectKey);
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User", username));
        ProjectMember member = memberRepository.findByProjectIdAndUserId(project.getId(), user.getId())
                .orElseThrow(() -> new NotFoundException("Membership", username));
        memberRepository.delete(member);
    }

    private Project project(String projectKey) {
        return projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
    }
}
