package io.github.forrestknight.buoy.domain;

import io.github.forrestknight.buoy.domain.Rollout.WeightedVariation;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based contract of the bucketing engine (spec 0001): the example and
 * golden tests in {@link BucketingTest} pin exact values; these properties must
 * hold for arbitrary inputs jqwik can dream up.
 */
class BucketingPropertiesTest {

    @Property
    void bucketingIsDeterministicAndInRange(
            @ForAll @StringLength(min = 1, max = 60) @AlphaChars String flagKey,
            @ForAll @StringLength(min = 1, max = 60) @AlphaChars String contextKey) {
        int bucket = Bucketing.bucketOf(flagKey, contextKey);
        assertThat(Bucketing.bucketOf(flagKey, contextKey)).isEqualTo(bucket);
        assertThat(bucket).isBetween(0, Bucketing.BUCKET_SPACE - 1);
    }

    /**
     * Minimal reallocation, stated as monotonicity: growing a variation's weight
     * may only add members, never evict one who already had it.
     */
    @Property
    void growingAWeightNeverEvictsExistingMembers(
            @ForAll @IntRange(max = 100_000) int smallerWeight,
            @ForAll @IntRange(max = 100_000) int largerWeight,
            @ForAll @StringLength(min = 1, max = 40) @AlphaChars String contextKey) {
        Assume.that(smallerWeight <= largerWeight);
        int bucket = Bucketing.bucketOf("rollout-flag", contextKey);
        boolean before = Bucketing.variationFor(bucket, twoWaySplit(smallerWeight));
        boolean after = Bucketing.variationFor(bucket, twoWaySplit(largerWeight));
        if (before) {
            assertThat(after).isTrue();
        }
    }

    @Property(tries = 20)
    void weightsAreHonoredWithinStatisticalTolerance(
            @ForAll @IntRange(min = 5_000, max = 95_000) int trueWeight,
            @ForAll @StringLength(min = 1, max = 20) @AlphaChars String flagKey) {
        int population = 20_000;
        long served = IntStream.range(0, population)
                .filter(i -> Bucketing.variationFor(
                        Bucketing.bucketOf(flagKey, "user-" + i), twoWaySplit(trueWeight)))
                .count();
        double expected = population * (trueWeight / 100_000.0);
        double tolerance = population * 0.02;   // ±2pp is > 5 sigma at n=20k
        assertThat((double) served).isBetween(expected - tolerance, expected + tolerance);
    }

    private static List<WeightedVariation> twoWaySplit(int trueWeightThousandths) {
        return List.of(new WeightedVariation(true, trueWeightThousandths),
                new WeightedVariation(false, Bucketing.BUCKET_SPACE - trueWeightThousandths));
    }
}
