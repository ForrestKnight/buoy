package io.github.forrestknight.buoy.domain;

import io.github.forrestknight.buoy.domain.Rollout.WeightedVariation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end evaluation semantics per brief §4: reason codes, rule ordering,
 * segment resolution, and sticky percentage rollouts.
 */
class EvaluatorTest {

    private final Evaluator evaluator = new Evaluator();

    private static final Map<String, List<Clause>> NO_SEGMENTS = Map.of();

    private static EvaluationContext user(String key, Map<String, Object> attributes) {
        return new EvaluationContext(key, attributes);
    }

    @Test
    void masterSwitchOffServesOffVariation() {
        EvaluableFlag flag = new EvaluableFlag("dark-mode", false,
                List.of(new TargetingRule("r1", List.of(
                        new Clause("plan", Operator.EQUALS, List.of("pro"))),
                        new Rollout.Fixed(true))),
                true, false);

        EvaluationResult result = evaluator.evaluate(flag, NO_SEGMENTS,
                user("user-1", Map.of("plan", "pro")));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.OFF);
        assertThat(result.matchedRuleId()).isNull();
    }

    @Test
    void noRulesFallsThroughToDefault() {
        EvaluableFlag flag = new EvaluableFlag("dark-mode", true, List.of(), true, false);

        EvaluationResult result = evaluator.evaluate(flag, NO_SEGMENTS, user("user-1", Map.of()));

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FALLTHROUGH);
    }

    @Test
    void firstMatchingRuleWinsInOrder() {
        EvaluableFlag flag = new EvaluableFlag("checkout", true,
                List.of(
                        new TargetingRule("rule-plan", List.of(
                                new Clause("plan", Operator.EQUALS, List.of("enterprise"))),
                                new Rollout.Fixed(false)),
                        new TargetingRule("rule-country", List.of(
                                new Clause("country", Operator.EQUALS, List.of("de"))),
                                new Rollout.Fixed(true))),
                false, false);

        // Matches both rules — the first one must win
        EvaluationResult result = evaluator.evaluate(flag, NO_SEGMENTS,
                user("user-1", Map.of("plan", "enterprise", "country", "de")));

        assertThat(result.matchedRuleId()).isEqualTo("rule-plan");
        assertThat(result.value()).isFalse();

        // Matches only the second
        EvaluationResult second = evaluator.evaluate(flag, NO_SEGMENTS,
                user("user-2", Map.of("plan", "pro", "country", "de")));
        assertThat(second.matchedRuleId()).isEqualTo("rule-country");
        assertThat(second.value()).isTrue();
    }

    @Test
    void clausesWithinARuleAreAnded() {
        EvaluableFlag flag = new EvaluableFlag("checkout", true,
                List.of(new TargetingRule("r1", List.of(
                        new Clause("plan", Operator.EQUALS, List.of("pro")),
                        new Clause("country", Operator.EQUALS, List.of("de"))),
                        new Rollout.Fixed(true))),
                false, false);

        assertThat(evaluator.evaluate(flag, NO_SEGMENTS,
                user("u", Map.of("plan", "pro", "country", "de"))).reason())
                .isEqualTo(EvaluationReason.RULE_MATCH);
        assertThat(evaluator.evaluate(flag, NO_SEGMENTS,
                user("u", Map.of("plan", "pro", "country", "us"))).reason())
                .isEqualTo(EvaluationReason.FALLTHROUGH);
    }

    @Test
    void segmentMembershipResolvesThroughSegmentClauses() {
        Map<String, List<Clause>> segments = Map.of("beta-testers", List.of(
                new Clause("email", Operator.ENDS_WITH, List.of("@acme.test"))));
        EvaluableFlag flag = new EvaluableFlag("new-ui", true,
                List.of(new TargetingRule("r1", List.of(
                        new Clause(null, Operator.IN_SEGMENT, List.of("beta-testers"))),
                        new Rollout.Fixed(true))),
                false, false);

        assertThat(evaluator.evaluate(flag, segments,
                user("u1", Map.of("email", "dev@acme.test"))).value()).isTrue();
        assertThat(evaluator.evaluate(flag, segments,
                user("u2", Map.of("email", "dev@other.test"))).reason())
                .isEqualTo(EvaluationReason.FALLTHROUGH);
    }

    @Test
    void notInSegmentMatchesOutsiders() {
        Map<String, List<Clause>> segments = Map.of("internal", List.of(
                new Clause("email", Operator.ENDS_WITH, List.of("@acme.test"))));
        EvaluableFlag flag = new EvaluableFlag("public-banner", true,
                List.of(new TargetingRule("r1", List.of(
                        new Clause(null, Operator.NOT_IN_SEGMENT, List.of("internal"))),
                        new Rollout.Fixed(true))),
                false, false);

        assertThat(evaluator.evaluate(flag, segments,
                user("u1", Map.of("email", "visitor@example.test"))).value()).isTrue();
        assertThat(evaluator.evaluate(flag, segments,
                user("u2", Map.of("email", "dev@acme.test"))).reason())
                .isEqualTo(EvaluationReason.FALLTHROUGH);
    }

    @Test
    void unknownSegmentIsNeverAMatch() {
        EvaluableFlag flag = new EvaluableFlag("new-ui", true,
                List.of(new TargetingRule("r1", List.of(
                        new Clause(null, Operator.IN_SEGMENT, List.of("deleted-segment"))),
                        new Rollout.Fixed(true))),
                false, false);

        assertThat(evaluator.evaluate(flag, NO_SEGMENTS, user("u1", Map.of())).reason())
                .isEqualTo(EvaluationReason.FALLTHROUGH);
    }

    @Test
    void percentageRolloutIsStickyAndRoughlyProportional() {
        EvaluableFlag flag = new EvaluableFlag("gradual-rollout", true,
                List.of(new TargetingRule("r1", List.of(
                        new Clause("key", Operator.STARTS_WITH, List.of("user-"))),
                        new Rollout.Percentage(List.of(
                                new WeightedVariation(true, 25_000),
                                new WeightedVariation(false, 75_000))))),
                false, false);

        long enabled = IntStream.range(0, 10_000)
                .filter(i -> evaluator.evaluate(flag, NO_SEGMENTS, user("user-" + i, Map.of())).value())
                .count();
        assertThat(enabled).isBetween(2_300L, 2_700L);   // 25% ± 2pp over 10k users

        // Stickiness: re-evaluating any user yields the identical result
        IntStream.range(0, 100).forEach(i -> {
            EvaluationResult first = evaluator.evaluate(flag, NO_SEGMENTS, user("user-" + i, Map.of()));
            EvaluationResult second = evaluator.evaluate(flag, NO_SEGMENTS, user("user-" + i, Map.of()));
            assertThat(second).isEqualTo(first);
        });
    }

    @Test
    void percentageRolloutStillReportsRuleMatch() {
        EvaluableFlag flag = new EvaluableFlag("gradual-rollout", true,
                List.of(new TargetingRule("r1", List.of(
                        new Clause("key", Operator.STARTS_WITH, List.of("user-"))),
                        new Rollout.Percentage(List.of(
                                new WeightedVariation(true, 50_000),
                                new WeightedVariation(false, 50_000))))),
                false, false);

        EvaluationResult result = evaluator.evaluate(flag, NO_SEGMENTS, user("user-1", Map.of()));
        assertThat(result.reason()).isEqualTo(EvaluationReason.RULE_MATCH);
        assertThat(result.matchedRuleId()).isEqualTo("r1");
    }
}
