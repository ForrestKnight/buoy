package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.FlagConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, Long> {

    Optional<FlagConfig> findByFlagIdAndEnvironmentId(Long flagId, Long environmentId);

    List<FlagConfig> findByEnvironmentId(Long environmentId);

    List<FlagConfig> findByFlagId(Long flagId);

    /** Snapshot load: one query for all configs of an environment with flags attached. */
    @Query("select c from FlagConfig c join fetch c.flag where c.environment.id = :environmentId")
    List<FlagConfig> findByEnvironmentIdWithFlag(Long environmentId);
}
