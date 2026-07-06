package io.github.forrestknight.buoy.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.domain.EvaluableFlag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.Segment;
import io.github.forrestknight.buoy.persistence.EnvironmentRepository;
import io.github.forrestknight.buoy.persistence.FlagConfigRepository;
import io.github.forrestknight.buoy.persistence.SegmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process cache of {@link EnvironmentSnapshot}s — the hot path of the
 * evaluation API reads exclusively from here; the database is touched only on
 * a miss. Entries are invalidated by domain events <em>after commit</em>, so a
 * concurrent evaluation can never re-populate the cache with data that is about
 * to be rolled back (or cache pre-commit state forever).
 */
@Component
public class EnvironmentSnapshotCache {

    private final FlagConfigRepository flagConfigRepository;
    private final SegmentRepository segmentRepository;
    private final EnvironmentRepository environmentRepository;
    private final LoadingCache<Long, EnvironmentSnapshot> cache;

    public EnvironmentSnapshotCache(FlagConfigRepository flagConfigRepository,
                                    SegmentRepository segmentRepository,
                                    EnvironmentRepository environmentRepository) {
        this.flagConfigRepository = flagConfigRepository;
        this.segmentRepository = segmentRepository;
        this.environmentRepository = environmentRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .build(this::load);
    }

    public EnvironmentSnapshot get(Long environmentId) {
        return cache.get(environmentId);
    }

    private EnvironmentSnapshot load(Long environmentId) {
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment", String.valueOf(environmentId)));

        Map<String, EvaluableFlag> flags = new HashMap<>();
        for (FlagConfig config : flagConfigRepository.findByEnvironmentIdWithFlag(environmentId)) {
            if (!config.getFlag().isArchived()) {
                flags.put(config.getFlag().getKey(), EvaluableFlag.from(config));
            }
        }
        Map<String, List<Clause>> segments = new HashMap<>();
        for (Segment segment : segmentRepository.findByProjectId(environment.getProject().getId())) {
            segments.put(segment.getKey(), segment.getClauses());
        }
        return new EnvironmentSnapshot(flags, segments);
    }

    @TransactionalEventListener
    public void onFlagChanged(FlagChangedEvent event) {
        cache.invalidate(event.environmentId());
    }

    @TransactionalEventListener
    public void onSegmentChanged(SegmentChangedEvent event) {
        for (Environment environment : environmentRepository.findByProjectId(event.projectId())) {
            cache.invalidate(environment.getId());
        }
    }

    @TransactionalEventListener
    public void onEnvironmentRemoved(EnvironmentRemovedEvent event) {
        cache.invalidate(event.environmentId());
    }
}
