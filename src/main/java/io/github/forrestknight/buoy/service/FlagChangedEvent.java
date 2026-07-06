package io.github.forrestknight.buoy.service;

/**
 * Published (in-transaction) whenever a flag's effective configuration in one
 * environment may have changed — creation, config update, archive/unarchive,
 * deletion. Consumed after commit by the snapshot cache, and designed as the
 * fan-out seam for a future live-update streaming layer ({@code flag.updated} /
 * {@code flag.deleted} events per environment).
 */
public record FlagChangedEvent(String projectKey, String environmentKey, Long environmentId,
                               String flagKey, Kind kind) {

    public enum Kind {
        UPDATED,
        DELETED
    }
}
