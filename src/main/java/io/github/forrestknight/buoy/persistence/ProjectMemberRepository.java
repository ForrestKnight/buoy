package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.ProjectMember;
import io.github.forrestknight.buoy.domain.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProjectId(Long projectId);

    List<ProjectMember> findByUserId(Long userId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    @Query("select m.role from ProjectMember m where m.project.id = :projectId and m.user.username = :username")
    Optional<ProjectRole> findRoleByProjectIdAndUsername(Long projectId, String username);

    @Query("select m.project from ProjectMember m where m.user.username = :username")
    List<Project> findProjectsByUsername(String username);
}
