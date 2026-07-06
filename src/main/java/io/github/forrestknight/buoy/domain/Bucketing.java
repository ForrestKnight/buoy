package io.github.forrestknight.buoy.domain;

import org.apache.commons.codec.digest.MurmurHash3;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic, sticky bucketing for percentage rollouts:
 * {@code bucket = murmur3_128(flagKey + ":" + contextKey) mod 100_000}.
 * The same context always lands in the same bucket for the same flag, and
 * weight changes only move contexts at range boundaries (minimal reallocation).
 * Never randomness.
 *
 * <p>Known v1 limitation: all rollouts on the same flag share this bucket —
 * a context in the top decile for one rule is in it for every rule on that
 * flag. Decorrelating requires a per-rule salt in the hash input, which
 * changes every existing allocation, so it is deliberately not done here.
 */
public final class Bucketing {

    public static final int BUCKET_SPACE = 100_000;

    public static int bucketOf(String flagKey, String contextKey) {
        byte[] input = (flagKey + ":" + contextKey).getBytes(StandardCharsets.UTF_8);
        long[] hash = MurmurHash3.hash128x64(input);
        return (int) Math.floorMod(hash[0], BUCKET_SPACE);
    }

    /** Maps a bucket onto contiguous weight ranges, in declaration order. */
    public static boolean variationFor(int bucket, java.util.List<Rollout.WeightedVariation> weights) {
        int cumulative = 0;
        for (Rollout.WeightedVariation weight : weights) {
            cumulative += weight.weightThousandths();
            if (bucket < cumulative) {
                return weight.variation();
            }
        }
        // Weights are validated to sum to BUCKET_SPACE; this line is unreachable
        // for valid data but keeps the method total.
        return weights.getLast().variation();
    }

    private Bucketing() {
    }
}
