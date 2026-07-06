package io.github.forrestknight.buoy.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * What a matched rule serves: a fixed variation, or a deterministic percentage split.
 * Weights are in thousandths of a percent and must sum to 100_000 (the bucket space).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Rollout.Fixed.class, name = "FIXED"),
        @JsonSubTypes.Type(value = Rollout.Percentage.class, name = "PERCENTAGE")
})
public sealed interface Rollout {

    record Fixed(boolean variation) implements Rollout {
    }

    record Percentage(List<WeightedVariation> weights) implements Rollout {

        public Percentage {
            weights = weights == null ? List.of() : List.copyOf(weights);
        }
    }

    record WeightedVariation(boolean variation, int weightThousandths) {
    }
}
