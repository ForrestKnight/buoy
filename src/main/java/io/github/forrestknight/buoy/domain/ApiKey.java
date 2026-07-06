package io.github.forrestknight.buoy.domain;

import org.jspecify.annotations.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * An environment-scoped API token. Only the SHA-256 of the token is stored; the
 * plaintext is shown once at creation. The prefix (e.g. {@code buoy_srv_a1b2})
 * is kept for display so keys are identifiable in lists without being usable.
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyKind kind;

    @Column(nullable = false)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false)
    private String tokenPrefix;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(Environment environment, ApiKeyKind kind, String name, String tokenHash, String tokenPrefix) {
        this.environment = environment;
        this.kind = kind;
        this.name = name;
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
    }

    public Long getId() {
        return id;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public ApiKeyKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }
}
