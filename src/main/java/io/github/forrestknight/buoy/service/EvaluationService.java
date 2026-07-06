package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.EvaluableFlag;
import io.github.forrestknight.buoy.domain.EvaluationContext;
import io.github.forrestknight.buoy.domain.EvaluationReason;
import io.github.forrestknight.buoy.domain.EvaluationResult;
import io.github.forrestknight.buoy.domain.Evaluator;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The evaluation hot path: everything comes from the in-process snapshot cache;
 * the database is never touched here (brief §4 performance target).
 */
@Service
public class EvaluationService {

    private final EnvironmentSnapshotCache snapshotCache;
    private final Evaluator evaluator = new Evaluator();

    public EvaluationService(EnvironmentSnapshotCache snapshotCache) {
        this.snapshotCache = snapshotCache;
    }

    /**
     * @param defaultValue served with reason {@code FLAG_NOT_FOUND} when the flag
     *                     is unknown or archived — "the in-code default provided
     *                     by the caller" (brief §4.1)
     */
    public EvaluationResult evaluate(Long environmentId, String flagKey,
                                     EvaluationContext context, boolean defaultValue) {
        EnvironmentSnapshot snapshot = snapshotCache.get(environmentId);
        EvaluableFlag flag = snapshot.flags().get(flagKey);
        if (flag == null) {
            return EvaluationResult.of(defaultValue, EvaluationReason.FLAG_NOT_FOUND);
        }
        return evaluator.evaluate(flag, snapshot.segments(), context);
    }

    /** Evaluates every (non-archived) flag in the environment, keyed by flag key. */
    public Map<String, EvaluationResult> evaluateAll(Long environmentId, EvaluationContext context) {
        EnvironmentSnapshot snapshot = snapshotCache.get(environmentId);
        Map<String, EvaluationResult> results = new LinkedHashMap<>();
        for (var entry : new TreeMap<>(snapshot.flags()).entrySet()) {
            results.put(entry.getKey(), evaluator.evaluate(entry.getValue(), snapshot.segments(), context));
        }
        return results;
    }
}
