package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.Flag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.FlagType;
import io.github.forrestknight.buoy.domain.TargetingRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class FlagDtos {

    public record CreateFlagRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = ApiValidation.KEY_PATTERN, message = ApiValidation.KEY_MESSAGE)
            String key,
            @NotBlank @Size(max = 200)
            String name,
            @Size(max = 2000)
            String description,
            List<@NotBlank @Size(max = 50) String> tags) {
    }

    public record UpdateFlagRequest(
            @NotBlank @Size(max = 200)
            String name,
            @Size(max = 2000)
            String description,
            List<@NotBlank @Size(max = 50) String> tags,
            @NotNull
            Boolean archived) {
    }

    public record FlagResponse(String key, String name, String description, FlagType type,
                               List<String> tags, boolean archived, Instant createdAt, Instant updatedAt) {

        public static FlagResponse from(Flag flag) {
            return new FlagResponse(flag.getKey(), flag.getName(), flag.getDescription(), flag.getType(),
                    flag.getTags(), flag.isArchived(), flag.getCreatedAt(), flag.getUpdatedAt());
        }
    }

    public record UpdateFlagConfigRequest(
            @NotNull
            Long version,
            @NotNull
            Boolean enabled,
            @NotNull
            List<TargetingRule> rules,
            @NotNull
            Boolean defaultVariation,
            @NotNull
            Boolean offVariation) {
    }

    public record FlagConfigResponse(String flagKey, String environmentKey, boolean enabled,
                                     List<TargetingRule> rules, boolean defaultVariation,
                                     boolean offVariation, long version, Instant updatedAt) {

        public static FlagConfigResponse from(FlagConfig config) {
            return new FlagConfigResponse(config.getFlag().getKey(), config.getEnvironment().getKey(),
                    config.isEnabled(), config.getRules(), config.getDefaultVariation(),
                    config.getOffVariation(), config.getVersion(), config.getUpdatedAt());
        }
    }

    private FlagDtos() {
    }
}
