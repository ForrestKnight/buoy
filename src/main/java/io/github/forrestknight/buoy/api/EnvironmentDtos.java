package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class EnvironmentDtos {

    public record CreateEnvironmentRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = ApiValidation.KEY_PATTERN, message = ApiValidation.KEY_MESSAGE)
            String key,
            @NotBlank @Size(max = 200)
            String name) {
    }

    public record UpdateEnvironmentRequest(
            @NotBlank @Size(max = 200)
            String name) {
    }

    public record EnvironmentResponse(String key, String name, Instant createdAt) {

        public static EnvironmentResponse from(Environment environment) {
            return new EnvironmentResponse(environment.getKey(), environment.getName(), environment.getCreatedAt());
        }
    }

    private EnvironmentDtos() {
    }
}
