package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.Segment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SegmentDtos {

    public record CreateSegmentRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = ApiValidation.KEY_PATTERN, message = ApiValidation.KEY_MESSAGE)
            String key,
            @NotBlank @Size(max = 200)
            String name,
            @Size(max = 2000)
            String description,
            @NotNull
            List<Clause> clauses) {
    }

    public record UpdateSegmentRequest(
            @NotBlank @Size(max = 200)
            String name,
            @Size(max = 2000)
            String description,
            @NotNull
            List<Clause> clauses) {
    }

    public record SegmentResponse(String key, String name, String description, List<Clause> clauses,
                                  Instant createdAt, Instant updatedAt) {

        public static SegmentResponse from(Segment segment) {
            return new SegmentResponse(segment.getKey(), segment.getName(), segment.getDescription(),
                    segment.getClauses(), segment.getCreatedAt(), segment.getUpdatedAt());
        }
    }

    private SegmentDtos() {
    }
}
