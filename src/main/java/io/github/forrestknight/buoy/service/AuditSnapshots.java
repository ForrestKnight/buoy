package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.ApiKey;
import io.github.forrestknight.buoy.domain.AppUser;
import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.domain.Flag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.ProjectMember;
import io.github.forrestknight.buoy.domain.Segment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The audited shape of each entity — what lands in the audit diff's
 * {@code before}/{@code after}. One place to see (and review) exactly which
 * fields are recorded; secrets (password/token hashes) are never included.
 */
final class AuditSnapshots {

    static Map<String, Object> of(Project project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", project.getKey());
        map.put("name", project.getName());
        map.put("description", project.getDescription());
        return map;
    }

    static Map<String, Object> of(Environment environment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", environment.getKey());
        map.put("name", environment.getName());
        return map;
    }

    static Map<String, Object> of(Flag flag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", flag.getKey());
        map.put("name", flag.getName());
        map.put("description", flag.getDescription());
        map.put("tags", List.copyOf(flag.getTags()));
        map.put("archived", flag.isArchived());
        return map;
    }

    static Map<String, Object> of(FlagConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", config.isEnabled());
        map.put("rules", List.copyOf(config.getRules()));
        map.put("defaultVariation", config.getDefaultVariation());
        map.put("offVariation", config.getOffVariation());
        return map;
    }

    static Map<String, Object> of(Segment segment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", segment.getKey());
        map.put("name", segment.getName());
        map.put("description", segment.getDescription());
        map.put("clauses", List.copyOf(segment.getClauses()));
        return map;
    }

    static Map<String, Object> of(ApiKey key) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", key.getKind());
        map.put("name", key.getName());
        map.put("tokenPrefix", key.getTokenPrefix());
        map.put("revokedAt", key.getRevokedAt());
        return map;
    }

    static Map<String, Object> of(AppUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("displayName", user.getDisplayName());
        map.put("instanceAdmin", user.isInstanceAdmin());
        return map;
    }

    static Map<String, Object> of(ProjectMember member) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", member.getUser().getUsername());
        map.put("role", member.getRole());
        return map;
    }

    private AuditSnapshots() {
    }
}
