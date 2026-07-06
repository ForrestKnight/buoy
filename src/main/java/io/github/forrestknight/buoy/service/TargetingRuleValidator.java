package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.Operator;
import io.github.forrestknight.buoy.domain.Rollout;
import io.github.forrestknight.buoy.domain.TargetingRule;
import io.github.forrestknight.buoy.persistence.SegmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Semantic validation of targeting documents, beyond what bean validation can express.
 * Also assigns stable ids to rules that arrive without one.
 */
@Component
public class TargetingRuleValidator {

    static final int BUCKET_SPACE = 100_000;

    private final SegmentRepository segmentRepository;

    public TargetingRuleValidator(SegmentRepository segmentRepository) {
        this.segmentRepository = segmentRepository;
    }

    /**
     * Validates rules for a flag config in the given project and returns a copy
     * with server-assigned ids where missing.
     */
    public List<TargetingRule> validateAndAssignIds(Long projectId, List<TargetingRule> rules) {
        return rules.stream().map(rule -> {
            validateRule(projectId, rule);
            String id = rule.id() == null || rule.id().isBlank() ? UUID.randomUUID().toString() : rule.id();
            return new TargetingRule(id, rule.clauses(), rule.rollout());
        }).toList();
    }

    /**
     * Segment clauses are plain conditions only — segments may not reference segments.
     */
    public void validateSegmentClauses(List<Clause> clauses) {
        for (Clause clause : clauses) {
            if (clause.operator() == Operator.IN_SEGMENT || clause.operator() == Operator.NOT_IN_SEGMENT) {
                throw new DomainValidationException("Segments cannot reference other segments");
            }
            validateClause(clause);
        }
    }

    private void validateRule(Long projectId, TargetingRule rule) {
        if (rule.clauses().isEmpty()) {
            throw new DomainValidationException("A targeting rule must have at least one clause");
        }
        for (Clause clause : rule.clauses()) {
            validateClause(clause);
            if (clause.operator() == Operator.IN_SEGMENT || clause.operator() == Operator.NOT_IN_SEGMENT) {
                for (String segmentKey : clause.values()) {
                    if (segmentRepository.findByProjectIdAndKey(projectId, segmentKey).isEmpty()) {
                        throw new DomainValidationException("Unknown segment '" + segmentKey + "'");
                    }
                }
            }
        }
        switch (rule.rollout()) {
            case null -> throw new DomainValidationException("A targeting rule must have a rollout");
            case Rollout.Fixed ignored -> {
            }
            case Rollout.Percentage percentage -> validateWeights(percentage.weights());
        }
    }

    private void validateClause(Clause clause) {
        if (clause.operator() == null) {
            throw new DomainValidationException("Clause operator is required");
        }
        boolean segmentClause = clause.operator() == Operator.IN_SEGMENT
                || clause.operator() == Operator.NOT_IN_SEGMENT;
        if (!segmentClause && (clause.attribute() == null || clause.attribute().isBlank())) {
            throw new DomainValidationException("Clause attribute is required for operator " + clause.operator().json());
        }
        if (clause.values().isEmpty()) {
            throw new DomainValidationException("Clause values must not be empty");
        }
    }

    private void validateWeights(List<Rollout.WeightedVariation> weights) {
        if (weights.isEmpty()) {
            throw new DomainValidationException("Percentage rollout requires at least one weighted variation");
        }
        int sum = 0;
        for (Rollout.WeightedVariation weight : weights) {
            if (weight.weightThousandths() < 0 || weight.weightThousandths() > BUCKET_SPACE) {
                throw new DomainValidationException("Weights must be between 0 and " + BUCKET_SPACE + " thousandths of a percent");
            }
            sum += weight.weightThousandths();
        }
        if (sum != BUCKET_SPACE) {
            throw new DomainValidationException("Percentage rollout weights must sum to " + BUCKET_SPACE + " (100%), got " + sum);
        }
    }
}
