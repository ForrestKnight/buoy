package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.ApiKey;
import io.github.forrestknight.buoy.domain.ApiKeyKind;
import io.github.forrestknight.buoy.service.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{environmentKey}/api-keys")
@PreAuthorize("@projectAccess.isOwner(authentication, #projectKey)")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    public record CreateApiKeyRequest(@NotNull ApiKeyKind kind, @NotBlank @Size(max = 200) String name) {
    }

    public record ApiKeyResponse(Long id, ApiKeyKind kind, String name, String tokenPrefix,
                                 Instant createdAt, Instant revokedAt) {

        static ApiKeyResponse from(ApiKey key) {
            return new ApiKeyResponse(key.getId(), key.getKind(), key.getName(), key.getTokenPrefix(),
                    key.getCreatedAt(), key.getRevokedAt());
        }
    }

    /** {@code token} is present only in this response, at creation time. */
    public record IssuedApiKeyResponse(Long id, ApiKeyKind kind, String name, String tokenPrefix,
                                       String token, Instant createdAt) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedApiKeyResponse create(@PathVariable String projectKey, @PathVariable String environmentKey,
                                       @Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyService.IssuedKey issued = apiKeyService.create(projectKey, environmentKey,
                request.kind(), request.name());
        return new IssuedApiKeyResponse(issued.key().getId(), issued.key().getKind(), issued.key().getName(),
                issued.key().getTokenPrefix(), issued.token(), issued.key().getCreatedAt());
    }

    @GetMapping
    public List<ApiKeyResponse> list(@PathVariable String projectKey, @PathVariable String environmentKey) {
        return apiKeyService.list(projectKey, environmentKey).stream().map(ApiKeyResponse::from).toList();
    }

    /** Revokes (does not erase) the key, so it remains visible in listings. */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revoke(@PathVariable String projectKey, @PathVariable String environmentKey,
                                       @PathVariable Long keyId) {
        apiKeyService.revoke(projectKey, environmentKey, keyId);
        return ResponseEntity.noContent().build();
    }
}
