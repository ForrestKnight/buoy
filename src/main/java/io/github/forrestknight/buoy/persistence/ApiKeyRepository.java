package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByTokenHash(String tokenHash);

    /** Authentication-time lookup: one query resolves the key with its environment and project. */
    @Query("select k from ApiKey k join fetch k.environment e join fetch e.project where k.tokenHash = :tokenHash")
    Optional<ApiKey> findByTokenHashWithScope(String tokenHash);

    List<ApiKey> findByEnvironmentId(Long environmentId);
}
