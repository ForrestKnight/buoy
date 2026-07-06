package io.github.forrestknight.buoy.domain;

import java.util.List;

/**
 * One ordered targeting rule: all clauses must match (AND), then the rollout decides
 * the served variation. The id is a stable opaque string assigned at creation and is
 * echoed back in evaluation results as the matched rule id.
 */
public record TargetingRule(String id, List<Clause> clauses, Rollout rollout) {

    public TargetingRule {
        clauses = clauses == null ? List.of() : List.copyOf(clauses);
    }
}
