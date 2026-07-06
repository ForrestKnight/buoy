package io.github.forrestknight.buoy.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.forrestknight.buoy.config.ApiKeyAuthentication.ApiKeyPrincipal;
import io.github.forrestknight.buoy.domain.ApiKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Keeps API-key authentication off the evaluation hot path: a validated token
 * resolves to its principal from memory; the database is consulted only on
 * first sight of a token. Entries are keyed by token hash (plaintext tokens
 * are never retained) and dropped after commit when a key is revoked.
 * Unknown tokens are deliberately not negative-cached — a flood of garbage
 * tokens must not be able to evict legitimate entries.
 */
@Component
public class ApiKeyAuthenticationCache {

    private final ApiKeyService apiKeyService;
    private final Cache<String, ApiKeyPrincipal> byTokenHash = Caffeine.newBuilder()
            .maximumSize(100_000)
            .build();

    public ApiKeyAuthenticationCache(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    public Optional<ApiKeyPrincipal> authenticate(String token) {
        String tokenHash = ApiKeyService.sha256(token);
        ApiKeyPrincipal cached = byTokenHash.getIfPresent(tokenHash);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ApiKeyPrincipal> resolved = apiKeyService.authenticate(token).map(this::principalOf);
        resolved.ifPresent(principal -> byTokenHash.put(tokenHash, principal));
        return resolved;
    }

    private ApiKeyPrincipal principalOf(ApiKey key) {
        return new ApiKeyPrincipal(key.getId(), key.getKind(), key.getName(),
                key.getEnvironment().getId(), key.getEnvironment().getKey(),
                key.getEnvironment().getProject().getId(), key.getEnvironment().getProject().getKey());
    }

    @TransactionalEventListener
    public void onApiKeyRevoked(ApiKeyRevokedEvent event) {
        byTokenHash.invalidate(event.tokenHash());
    }
}
