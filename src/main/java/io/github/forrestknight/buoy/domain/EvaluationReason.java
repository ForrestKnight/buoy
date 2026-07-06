package io.github.forrestknight.buoy.domain;

public enum EvaluationReason {
    /** Flag unknown, archived, or config missing — the caller's in-code default was served. */
    FLAG_NOT_FOUND,
    /** Master switch off — the "off" variation was served. */
    OFF,
    /** A targeting rule matched — see {@code matchedRuleId}. */
    RULE_MATCH,
    /** No rule matched — the default variation was served. */
    FALLTHROUGH
}
