package io.github.forrestknight.buoy.service;

/**
 * Published when a segment changes. Segments are project-wide, so every
 * environment snapshot of the project is invalidated.
 */
public record SegmentChangedEvent(String projectKey, Long projectId, String segmentKey) {
}
