package io.github.forrestknight.buoy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-environment configuration of a flag. Rules live here as a JSONB document so a
 * config mutation is one atomic row update guarded by the optimistic-lock version.
 */
@Entity
@Table(name = "flag_config")
public class FlagConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id")
    private Flag flag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<TargetingRule> rules = new ArrayList<>();

    @Column(name = "default_variation", nullable = false)
    private boolean defaultVariation = true;

    @Column(name = "off_variation", nullable = false)
    private boolean offVariation = false;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlagConfig() {
    }

    public FlagConfig(Flag flag, Environment environment) {
        this.flag = flag;
        this.environment = environment;
    }

    public Long getId() {
        return id;
    }

    public Flag getFlag() {
        return flag;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<TargetingRule> getRules() {
        return rules;
    }

    public void setRules(List<TargetingRule> rules) {
        this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }

    public boolean getDefaultVariation() {
        return defaultVariation;
    }

    public void setDefaultVariation(boolean defaultVariation) {
        this.defaultVariation = defaultVariation;
    }

    public boolean getOffVariation() {
        return offVariation;
    }

    public void setOffVariation(boolean offVariation) {
        this.offVariation = offVariation;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
