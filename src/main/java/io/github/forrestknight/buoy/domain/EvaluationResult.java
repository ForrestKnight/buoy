package io.github.forrestknight.buoy.domain;

import org.jspecify.annotations.Nullable;
public record EvaluationResult(boolean value, EvaluationReason reason, @Nullable String matchedRuleId) {

    public static EvaluationResult of(boolean value, EvaluationReason reason) {
        return new EvaluationResult(value, reason, null);
    }

    public static EvaluationResult ruleMatch(boolean value, String ruleId) {
        return new EvaluationResult(value, EvaluationReason.RULE_MATCH, ruleId);
    }
}
