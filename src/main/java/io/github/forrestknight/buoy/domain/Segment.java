package io.github.forrestknight.buoy.domain;

import org.jspecify.annotations.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A named, reusable set of clauses (ANDed). Rules reference segments through the
 * IN_SEGMENT / NOT_IN_SEGMENT operators; segment clauses may not themselves
 * reference segments (no nesting — enforced at the API boundary).
 */
@Entity
@Table(name = "segment")
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Clause> clauses = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Segment() {
    }

    public Segment(Project project, String key, String name, @Nullable String description, @Nullable List<Clause> clauses) {
        this.project = project;
        this.key = key;
        this.name = name;
        this.description = description;
        this.clauses = clauses == null ? new ArrayList<>() : new ArrayList<>(clauses);
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public List<Clause> getClauses() {
        return clauses;
    }

    public void setClauses(@Nullable List<Clause> clauses) {
        this.clauses = clauses == null ? new ArrayList<>() : new ArrayList<>(clauses);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
