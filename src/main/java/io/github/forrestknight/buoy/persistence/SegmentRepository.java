package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.Segment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    List<Segment> findByProjectId(Long projectId);

    Optional<Segment> findByProjectIdAndKey(Long projectId, String key);

    boolean existsByProjectIdAndKey(Long projectId, String key);
}
