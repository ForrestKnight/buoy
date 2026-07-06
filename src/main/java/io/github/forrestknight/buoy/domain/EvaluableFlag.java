package io.github.forrestknight.buoy.domain;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Everything the evaluator needs to know about one flag in one environment —
 * a detached, immutable snapshot suitable for caching (no JPA entities inside).
 */
public record EvaluableFlag(String key, boolean enabled, @Nullable List<TargetingRule> rules,
                            boolean defaultVariation, boolean offVariation) {

    public EvaluableFlag {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static EvaluableFlag from(FlagConfig config) {
        return new EvaluableFlag(config.getFlag().getKey(), config.isEnabled(), config.getRules(),
                config.getDefaultVariation(), config.getOffVariation());
    }
}
