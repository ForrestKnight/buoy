package io.github.forrestknight.buoy.domain;

import java.util.Map;

/**
 * What SDKs send: a required entity key (the bucketing identity) plus arbitrary
 * attributes ({@code plan}, {@code country}, {@code appVersion}, ...). The special
 * attribute name {@code key} resolves to the context key itself.
 */
public record EvaluationContext(String key, Map<String, Object> attributes) {

    public EvaluationContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** @return the attribute value, or {@code null} when absent. */
    public Object attribute(String name) {
        if ("key".equals(name)) {
            return key;
        }
        return attributes.get(name);
    }
}
