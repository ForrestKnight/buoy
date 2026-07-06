package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.config.ApiKeyAuthentication;
import io.github.forrestknight.buoy.domain.ApiKeyKind;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.ProjectRole;
import io.github.forrestknight.buoy.persistence.ProjectMemberRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Per-project RBAC, referenced from {@code @PreAuthorize("@projectAccess....")}.
 * Instance admins bypass membership checks; ADMIN API keys act as automation
 * with editor-level access on their own project only.
 */
@Component("projectAccess")
@Transactional(readOnly = true)
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectAccessService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    /** Any console user may create a project (becoming its owner); API keys may not. */
    public boolean canCreateProject(Authentication authentication) {
        return authentication.getPrincipal() instanceof Jwt;
    }

    public boolean canRead(Authentication authentication, String projectKey) {
        return check(authentication, projectKey,
                Set.of(ProjectRole.VIEWER, ProjectRole.EDITOR, ProjectRole.OWNER), true);
    }

    public boolean canEdit(Authentication authentication, String projectKey) {
        return check(authentication, projectKey,
                Set.of(ProjectRole.EDITOR, ProjectRole.OWNER), true);
    }

    public boolean isOwner(Authentication authentication, String projectKey) {
        return check(authentication, projectKey, Set.of(ProjectRole.OWNER), false);
    }

    public List<Project> visibleProjects(Authentication authentication) {
        if (isInstanceAdmin(authentication)) {
            return projectRepository.findAll();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return memberRepository.findProjectsByUsername(jwt.getSubject());
        }
        if (authentication instanceof ApiKeyAuthentication apiKey
                && apiKey.getPrincipal().kind() == ApiKeyKind.ADMIN) {
            return projectRepository.findByKey(apiKey.getPrincipal().projectKey())
                    .map(List::of).orElse(List.of());
        }
        return List.of();
    }

    private boolean check(Authentication authentication, String projectKey,
                          Set<ProjectRole> allowedRoles, boolean allowAdminKey) {
        if (isInstanceAdmin(authentication)) {
            return true;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return roleOf(jwt.getSubject(), projectKey).map(allowedRoles::contains).orElse(false);
        }
        if (authentication instanceof ApiKeyAuthentication apiKey) {
            return allowAdminKey
                    && apiKey.getPrincipal().kind() == ApiKeyKind.ADMIN
                    && apiKey.getPrincipal().projectKey().equals(projectKey);
        }
        return false;
    }

    private boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaim("instanceAdmin"));
    }

    private Optional<ProjectRole> roleOf(String username, String projectKey) {
        return projectRepository.findByKey(projectKey)
                .flatMap(project -> memberRepository.findRoleByProjectIdAndUsername(project.getId(), username));
    }
}
