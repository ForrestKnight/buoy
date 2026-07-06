package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    List<Environment> findByProjectId(Long projectId);

    Optional<Environment> findByProjectIdAndKey(Long projectId, String key);

    boolean existsByProjectIdAndKey(Long projectId, String key);
}
