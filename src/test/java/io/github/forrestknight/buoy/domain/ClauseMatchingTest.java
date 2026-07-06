package io.github.forrestknight.buoy.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven coverage of every operator: match, no-match, missing attribute,
 * and type-coercion behavior.
 */
class ClauseMatchingTest {

    private final Evaluator evaluator = new Evaluator();

    static Stream<Arguments> clauseCases() {
        return Stream.of(
                // operator, attribute value (null = absent), clause values, expected
                Arguments.of("equals matches", Operator.EQUALS, "pro", List.of("pro"), true),
                Arguments.of("equals rejects", Operator.EQUALS, "pro", List.of("enterprise"), false),
                Arguments.of("equals any-value-of semantics", Operator.EQUALS, "pro", List.of("enterprise", "pro"), true),
                Arguments.of("equals missing attribute", Operator.EQUALS, null, List.of("pro"), false),
                Arguments.of("equals stringifies numbers", Operator.EQUALS, 42, List.of("42"), true),
                Arguments.of("equals stringifies booleans", Operator.EQUALS, true, List.of("true"), true),

                Arguments.of("not_equals matches", Operator.NOT_EQUALS, "pro", List.of("enterprise"), true),
                Arguments.of("not_equals rejects", Operator.NOT_EQUALS, "pro", List.of("pro"), false),
                Arguments.of("not_equals missing attribute matches", Operator.NOT_EQUALS, null, List.of("pro"), true),

                Arguments.of("in matches", Operator.IN, "de", List.of("de", "fr", "nl"), true),
                Arguments.of("in rejects", Operator.IN, "us", List.of("de", "fr", "nl"), false),
                Arguments.of("in missing attribute", Operator.IN, null, List.of("de"), false),

                Arguments.of("not_in matches", Operator.NOT_IN, "us", List.of("de", "fr"), true),
                Arguments.of("not_in rejects", Operator.NOT_IN, "de", List.of("de", "fr"), false),
                Arguments.of("not_in missing attribute matches", Operator.NOT_IN, null, List.of("de"), true),

                Arguments.of("contains matches", Operator.CONTAINS, "user@acme.test", List.of("@acme"), true),
                Arguments.of("contains rejects", Operator.CONTAINS, "user@other.test", List.of("@acme"), false),
                Arguments.of("contains missing attribute", Operator.CONTAINS, null, List.of("@acme"), false),

                Arguments.of("starts_with matches", Operator.STARTS_WITH, "eu-west-1", List.of("eu-"), true),
                Arguments.of("starts_with rejects", Operator.STARTS_WITH, "us-east-1", List.of("eu-"), false),

                Arguments.of("ends_with matches", Operator.ENDS_WITH, "user@acme.test", List.of("@acme.test"), true),
                Arguments.of("ends_with rejects", Operator.ENDS_WITH, "user@acme.example", List.of("@acme.test"), false),

                Arguments.of("greater_than numeric attr", Operator.GREATER_THAN, 5, List.of("3"), true),
                Arguments.of("greater_than equal is false", Operator.GREATER_THAN, 3, List.of("3"), false),
                Arguments.of("greater_than parses string attr", Operator.GREATER_THAN, "10", List.of("9"), true),
                Arguments.of("greater_than lexicographic trap: 10 > 9 numerically", Operator.GREATER_THAN, 10, List.of("9"), true),
                Arguments.of("greater_than non-numeric attr", Operator.GREATER_THAN, "abc", List.of("3"), false),
                Arguments.of("greater_than non-numeric clause value", Operator.GREATER_THAN, 5, List.of("abc"), false),
                Arguments.of("greater_than missing attribute", Operator.GREATER_THAN, null, List.of("3"), false),

                Arguments.of("less_than matches", Operator.LESS_THAN, 2, List.of("3"), true),
                Arguments.of("less_than rejects", Operator.LESS_THAN, 4, List.of("3"), false),
                Arguments.of("less_than decimal", Operator.LESS_THAN, 2.5, List.of("2.6"), true),

                Arguments.of("semver_gt matches", Operator.SEMVER_GT, "2.1.1", List.of("2.1.0"), true),
                Arguments.of("semver_gt equal is false", Operator.SEMVER_GT, "2.1.0", List.of("2.1.0"), false),
                Arguments.of("semver_gte equal is true", Operator.SEMVER_GTE, "2.1.0", List.of("2.1.0"), true),
                Arguments.of("semver_gte 2.10 vs 2.9 numeric segments", Operator.SEMVER_GTE, "2.10.0", List.of("2.9.0"), true),
                Arguments.of("semver_lt prerelease below release", Operator.SEMVER_LT, "1.0.0-beta", List.of("1.0.0"), true),
                Arguments.of("semver_lte matches", Operator.SEMVER_LTE, "1.2.3", List.of("1.2.3"), true),
                Arguments.of("semver invalid attr", Operator.SEMVER_GTE, "not-a-version", List.of("1.0.0"), false),
                Arguments.of("semver invalid clause value", Operator.SEMVER_GTE, "1.0.0", List.of("newest"), false),
                Arguments.of("semver missing attribute", Operator.SEMVER_GTE, null, List.of("1.0.0"), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clauseCases")
    void clauseSemantics(String description, Operator operator, Object attributeValue,
                         List<String> clauseValues, boolean expectedMatch) {
        Map<String, Object> attributes = attributeValue == null
                ? Map.of()
                : Map.of("attr", attributeValue);
        EvaluableFlag flag = new EvaluableFlag("test-flag", true,
                List.of(new TargetingRule("rule-1",
                        List.of(new Clause("attr", operator, clauseValues)),
                        new Rollout.Fixed(true))),
                false, false);

        EvaluationResult result = evaluator.evaluate(flag, Map.of(),
                new EvaluationContext("user-1", attributes));

        if (expectedMatch) {
            assertThat(result.reason()).as(description).isEqualTo(EvaluationReason.RULE_MATCH);
            assertThat(result.value()).isTrue();
        } else {
            assertThat(result.reason()).as(description).isEqualTo(EvaluationReason.FALLTHROUGH);
            assertThat(result.value()).isFalse();
        }
    }

    @ParameterizedTest(name = "key attribute resolves to context key: {0}")
    @MethodSource("keyAttributeCases")
    void keyAttributeResolvesToContextKey(String description, Operator operator,
                                          List<String> values, boolean expected) {
        EvaluableFlag flag = new EvaluableFlag("test-flag", true,
                List.of(new TargetingRule("rule-1",
                        List.of(new Clause("key", operator, values)),
                        new Rollout.Fixed(true))),
                false, false);

        EvaluationResult result = evaluator.evaluate(flag, Map.of(),
                new EvaluationContext("user-42", Map.of()));

        assertThat(result.reason()).as(description)
                .isEqualTo(expected ? EvaluationReason.RULE_MATCH : EvaluationReason.FALLTHROUGH);
    }

    static Stream<Arguments> keyAttributeCases() {
        return Stream.of(
                Arguments.of("equals", Operator.EQUALS, List.of("user-42"), true),
                Arguments.of("in", Operator.IN, List.of("user-1", "user-42"), true),
                Arguments.of("starts_with", Operator.STARTS_WITH, List.of("user-"), true),
                Arguments.of("no match", Operator.EQUALS, List.of("user-1"), false));
    }
}
