package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProjectDtos {

    public record CreateProjectRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = ApiValidation.KEY_PATTERN, message = ApiValidation.KEY_MESSAGE)
            String key,
            @NotBlank @Size(max = 200)
            String name,
            @Size(max = 2000)
            String description) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 200)
            String name,
            @Size(max = 2000)
            String description) {
    }

    public record ProjectResponse(String key, String name, String description,
                                  Instant createdAt, Instant updatedAt) {

        public static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getKey(), project.getName(), project.getDescription(),
                    project.getCreatedAt(), project.getUpdatedAt());
        }
    }

    private ProjectDtos() {
    }
}
