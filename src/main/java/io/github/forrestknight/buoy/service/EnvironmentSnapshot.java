package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.EvaluableFlag;

import java.util.List;
import java.util.Map;

/**
 * Everything needed to evaluate any flag in one environment, fully detached
 * from persistence. Archived flags are excluded at load time (they evaluate
 * as FLAG_NOT_FOUND per brief §4.1).
 */
public record EnvironmentSnapshot(Map<String, EvaluableFlag> flags,
                                  Map<String, List<Clause>> segments) {

    public EnvironmentSnapshot {
        flags = Map.copyOf(flags);
        segments = Map.copyOf(segments);
    }
}
