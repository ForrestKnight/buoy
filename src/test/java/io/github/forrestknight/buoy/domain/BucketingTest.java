package io.github.forrestknight.buoy.domain;

import io.github.forrestknight.buoy.domain.Rollout.WeightedVariation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stickiness and distribution properties the brief demands (§4.4).
 * These tests define the permanent bucketing contract: if any of them fails
 * after a refactor, existing production allocations would shift — that is
 * a breaking change, not a test to update.
 */
class BucketingTest {

    @Test
    void bucketIsDeterministic() {
        int first = Bucketing.bucketOf("checkout-redesign", "user-1234");
        IntStream.range(0, 100).forEach(i ->
                assertThat(Bucketing.bucketOf("checkout-redesign", "user-1234")).isEqualTo(first));
    }

    @Test
    void bucketsDifferAcrossFlagsForTheSameUser() {
        long distinct = IntStream.range(0, 50)
                .map(i -> Bucketing.bucketOf("flag-" + i, "user-1234"))
                .distinct().count();
        assertThat(distinct).isGreaterThan(45);
    }

    @Test
    void bucketsAreUniformlyDistributed() {
        int users = 100_000;
        long inFirstQuarter = IntStream.range(0, users)
                .map(i -> Bucketing.bucketOf("some-flag", "user-" + i))
                .filter(bucket -> bucket < 25_000)
                .count();
        // 25% ± 1 percentage point over 100k samples
        assertThat(inFirstQuarter).isBetween(24_000L, 26_000L);
    }

    @Test
    void weightRangesMapInDeclarationOrder() {
        List<WeightedVariation> weights = List.of(
                new WeightedVariation(true, 25_000),
                new WeightedVariation(false, 75_000));
        assertThat(Bucketing.variationFor(0, weights)).isTrue();
        assertThat(Bucketing.variationFor(24_999, weights)).isTrue();
        assertThat(Bucketing.variationFor(25_000, weights)).isFalse();
        assertThat(Bucketing.variationFor(99_999, weights)).isFalse();
    }

    @Test
    void zeroWeightRangeIsNeverServed() {
        List<WeightedVariation> weights = List.of(
                new WeightedVariation(true, 0),
                new WeightedVariation(false, 100_000));
        IntStream.range(0, 1000).forEach(i ->
                assertThat(Bucketing.variationFor(Bucketing.bucketOf("f", "user-" + i), weights)).isFalse());
    }

    /**
     * Raising true's share from 25% to 50% must only move users whose buckets
     * fall in [25_000, 50_000) — everyone already on true stays on true.
     */
    @Test
    void weightChangesReallocateMinimally() {
        List<WeightedVariation> before = List.of(
                new WeightedVariation(true, 25_000),
                new WeightedVariation(false, 75_000));
        List<WeightedVariation> after = List.of(
                new WeightedVariation(true, 50_000),
                new WeightedVariation(false, 50_000));

        for (int i = 0; i < 10_000; i++) {
            int bucket = Bucketing.bucketOf("rollout-flag", "user-" + i);
            boolean servedBefore = Bucketing.variationFor(bucket, before);
            boolean servedAfter = Bucketing.variationFor(bucket, after);
            if (servedBefore) {
                assertThat(servedAfter).as("user in the original 25%% must stay on true").isTrue();
            }
            if (bucket >= 50_000) {
                assertThat(servedAfter).as("user beyond the new 50%% stays on false").isFalse();
            }
        }
    }

    /** Golden values: pin the exact hash-to-bucket mapping. If these move, every
     *  production allocation moves — see class Javadoc. Values generated from
     *  commons-codec MurmurHash3.hash128x64 (its default seed 104729). */
    @Test
    void goldenBuckets() {
        assertThat(Bucketing.bucketOf("checkout-redesign", "user-1")).isEqualTo(GOLDEN_1);
        assertThat(Bucketing.bucketOf("checkout-redesign", "user-2")).isEqualTo(GOLDEN_2);
        assertThat(Bucketing.bucketOf("new-payment-flow", "user-1")).isEqualTo(GOLDEN_3);
    }

    private static final int GOLDEN_1 = 71_580;
    private static final int GOLDEN_2 = 88_861;
    private static final int GOLDEN_3 = 13_459;
}
