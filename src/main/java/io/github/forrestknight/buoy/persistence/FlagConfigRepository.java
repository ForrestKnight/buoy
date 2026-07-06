package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.FlagConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, Long> {

    Optional<FlagConfig> findByFlagIdAndEnvironmentId(Long flagId, Long environmentId);

    List<FlagConfig> findByEnvironmentId(Long environmentId);

    List<FlagConfig> findByFlagId(Long flagId);
}
