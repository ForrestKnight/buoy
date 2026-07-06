package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByTokenHash(String tokenHash);

    List<ApiKey> findByEnvironmentId(Long environmentId);
}
