package io.github.forrestknight.buoy.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Clause operators. JSON names are snake_case and part of the public API contract.
 */
public enum Operator {
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    IN("in"),
    NOT_IN("not_in"),
    CONTAINS("contains"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    GREATER_THAN("greater_than"),
    LESS_THAN("less_than"),
    SEMVER_GT("semver_gt"),
    SEMVER_GTE("semver_gte"),
    SEMVER_LT("semver_lt"),
    SEMVER_LTE("semver_lte"),
    IN_SEGMENT("in_segment"),
    NOT_IN_SEGMENT("not_in_segment");

    private final String json;

    Operator(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }
}
