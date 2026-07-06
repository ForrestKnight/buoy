package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    List<Flag> findByProjectId(Long projectId);

    Optional<Flag> findByProjectIdAndKey(Long projectId, String key);

    boolean existsByProjectIdAndKey(Long projectId, String key);
}
