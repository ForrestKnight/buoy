package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.ApiKey;
import io.github.forrestknight.buoy.domain.ApiKeyKind;
import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.persistence.ApiKeyRepository;
import io.github.forrestknight.buoy.persistence.EnvironmentRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ApiKeyService {

    public static final String SERVER_SDK_PREFIX = "buoy_srv_";
    public static final String ADMIN_PREFIX = "buoy_adm_";

    private final ApiKeyRepository apiKeyRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         EnvironmentRepository environmentRepository,
                         ProjectRepository projectRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
    }

    /** The plaintext token, returned exactly once at creation. */
    public record IssuedKey(ApiKey key, String token) {
    }

    public IssuedKey create(String projectKey, String environmentKey, ApiKeyKind kind, String name) {
        Environment environment = environment(projectKey, environmentKey);
        String prefix = kind == ApiKeyKind.SERVER_SDK ? SERVER_SDK_PREFIX : ADMIN_PREFIX;
        String secret = HexFormat.of().formatHex(randomBytes(32));
        String token = prefix + secret;
        ApiKey key = apiKeyRepository.save(new ApiKey(environment, kind, name,
                sha256(token), prefix + secret.substring(0, 4)));
        return new IssuedKey(key, token);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(String projectKey, String environmentKey) {
        return apiKeyRepository.findByEnvironmentId(environment(projectKey, environmentKey).getId());
    }

    public void revoke(String projectKey, String environmentKey, Long keyId) {
        Environment environment = environment(projectKey, environmentKey);
        ApiKey key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getEnvironment().getId().equals(environment.getId()))
                .orElseThrow(() -> new NotFoundException("API key", String.valueOf(keyId)));
        if (!key.isRevoked()) {
            key.revoke(Instant.now());
        }
    }

    /** Authentication-time lookup; empty when the token is unknown or revoked. */
    @Transactional(readOnly = true)
    public Optional<ApiKey> authenticate(String token) {
        return apiKeyRepository.findByTokenHashWithScope(sha256(token))
                .filter(key -> !key.isRevoked());
    }

    public static String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private Environment environment(String projectKey, String environmentKey) {
        var project = projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new NotFoundException("Project", projectKey));
        return environmentRepository.findByProjectIdAndKey(project.getId(), environmentKey)
                .orElseThrow(() -> new NotFoundException("Environment", environmentKey));
    }
}
