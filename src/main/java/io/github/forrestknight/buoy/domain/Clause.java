package io.github.forrestknight.buoy.domain;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A single condition: {@code attribute operator values}. For {@link Operator#IN_SEGMENT} and
 * {@link Operator#NOT_IN_SEGMENT} the values are segment keys and the attribute is ignored.
 * Values are strings; numeric and semver operators coerce at evaluation time (Unleash-style).
 */
public record Clause(@Nullable String attribute, Operator operator, List<String> values) {

    public Clause {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
