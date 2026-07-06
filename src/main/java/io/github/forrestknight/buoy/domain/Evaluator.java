package io.github.forrestknight.buoy.domain;

import java.util.List;
import java.util.Map;

/**
 * The evaluation engine (brief §4). Pure domain logic: no I/O, no framework,
 * everything it needs arrives as immutable snapshots.
 *
 * <p>Semantics:
 * <ol>
 *   <li>Master switch off → off variation, reason {@code OFF}.</li>
 *   <li>Rules walk in order; the first rule whose clauses all match (AND) wins
 *       and its rollout decides the value, reason {@code RULE_MATCH} + rule id.</li>
 *   <li>No match → default variation, reason {@code FALLTHROUGH}.</li>
 * </ol>
 * {@code FLAG_NOT_FOUND} is the caller's job — a missing/archived flag never
 * reaches this class.
 *
 * <p>Clause semantics: a clause matches when <em>any</em> of its values matches
 * the attribute (LaunchDarkly-style). Equality and string operators compare
 * stringified values; {@code greater_than}/{@code less_than} compare numerically;
 * {@code semver_*} operators compare per SemVer precedence — unparseable input
 * simply doesn't match. A missing attribute never matches positive operators and
 * always matches negative ones ({@code not_equals}, {@code not_in}).
 */
public class Evaluator {

    public EvaluationResult evaluate(EvaluableFlag flag, Map<String, List<Clause>> segments,
                                     EvaluationContext context) {
        if (!flag.enabled()) {
            return EvaluationResult.of(flag.offVariation(), EvaluationReason.OFF);
        }
        for (TargetingRule rule : flag.rules()) {
            if (allClausesMatch(rule.clauses(), segments, context)) {
                return EvaluationResult.ruleMatch(serve(flag.key(), rule.rollout(), context), rule.id());
            }
        }
        return EvaluationResult.of(flag.defaultVariation(), EvaluationReason.FALLTHROUGH);
    }

    private boolean serve(String flagKey, Rollout rollout, EvaluationContext context) {
        return switch (rollout) {
            case Rollout.Fixed fixed -> fixed.variation();
            case Rollout.Percentage percentage -> Bucketing.variationFor(
                    Bucketing.bucketOf(flagKey, context.key()), percentage.weights());
        };
    }

    private boolean allClausesMatch(List<Clause> clauses, Map<String, List<Clause>> segments,
                                    EvaluationContext context) {
        for (Clause clause : clauses) {
            if (!clauseMatches(clause, segments, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean clauseMatches(Clause clause, Map<String, List<Clause>> segments,
                                  EvaluationContext context) {
        return switch (clause.operator()) {
            case IN_SEGMENT -> inAnySegment(clause.values(), segments, context);
            case NOT_IN_SEGMENT -> !inAnySegment(clause.values(), segments, context);
            case NOT_EQUALS -> !attributeMatches(clause, context, Evaluator::stringEquals);
            case NOT_IN -> !attributeMatches(clause, context, Evaluator::stringEquals);
            case EQUALS, IN -> attributeMatches(clause, context, Evaluator::stringEquals);
            case CONTAINS -> attributeMatches(clause, context, (attr, value) -> attr.contains(value));
            case STARTS_WITH -> attributeMatches(clause, context, (attr, value) -> attr.startsWith(value));
            case ENDS_WITH -> attributeMatches(clause, context, (attr, value) -> attr.endsWith(value));
            case GREATER_THAN -> numericMatches(clause, context, comparison -> comparison > 0);
            case LESS_THAN -> numericMatches(clause, context, comparison -> comparison < 0);
            case SEMVER_GT -> semverMatches(clause, context, comparison -> comparison > 0);
            case SEMVER_GTE -> semverMatches(clause, context, comparison -> comparison >= 0);
            case SEMVER_LT -> semverMatches(clause, context, comparison -> comparison < 0);
            case SEMVER_LTE -> semverMatches(clause, context, comparison -> comparison <= 0);
        };
    }

    /**
     * A context is in a segment when the segment exists and all its clauses match;
     * an {@code in_segment} clause matches when the context is in any listed segment.
     * Unknown segment keys are simply not matches (dangling references are legal).
     */
    private boolean inAnySegment(List<String> segmentKeys, Map<String, List<Clause>> segments,
                                 EvaluationContext context) {
        for (String segmentKey : segmentKeys) {
            List<Clause> clauses = segments.get(segmentKey);
            if (clauses != null && !clauses.isEmpty() && allClausesMatch(clauses, segments, context)) {
                return true;
            }
        }
        return false;
    }

    private interface StringMatch {
        boolean test(String attribute, String clauseValue);
    }

    private boolean attributeMatches(Clause clause, EvaluationContext context, StringMatch match) {
        Object attribute = context.attribute(clause.attribute());
        if (attribute == null) {
            return false;
        }
        String value = String.valueOf(attribute);
        return clause.values().stream().anyMatch(candidate -> match.test(value, candidate));
    }

    private interface ComparisonMatch {
        boolean test(int comparison);
    }

    private boolean numericMatches(Clause clause, EvaluationContext context, ComparisonMatch match) {
        Object attribute = context.attribute(clause.attribute());
        Double attributeNumber = toNumber(attribute);
        if (attributeNumber == null) {
            return false;
        }
        return clause.values().stream().anyMatch(candidate -> {
            Double value = toNumber(candidate);
            return value != null && match.test(Double.compare(attributeNumber, value));
        });
    }

    private boolean semverMatches(Clause clause, EvaluationContext context, ComparisonMatch match) {
        Object attribute = context.attribute(clause.attribute());
        if (attribute == null) {
            return false;
        }
        return SemVer.parse(String.valueOf(attribute))
                .map(version -> clause.values().stream().anyMatch(candidate ->
                        SemVer.parse(candidate)
                                .map(bound -> match.test(version.compareTo(bound)))
                                .orElse(false)))
                .orElse(false);
    }

    private static boolean stringEquals(String attribute, String clauseValue) {
        return attribute.equals(clauseValue);
    }

    private static Double toNumber(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Number number -> {
                return number.doubleValue();
            }
            default -> {
                try {
                    return Double.parseDouble(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }
}
