package io.github.forrestknight.buoy.domain;

import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal SemVer 2.0.0 implementation for clause comparison: numeric
 * major.minor.patch, optional pre-release with spec precedence rules
 * (numeric identifiers compare numerically and rank below alphanumeric;
 * a pre-release ranks below its release). Build metadata is ignored.
 */
public record SemVer(int major, int minor, int patch, @Nullable String prerelease) implements Comparable<SemVer> {

    private static final Pattern FORMAT = Pattern.compile(
            "(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?");

    /** @return empty when the input is not a valid semver — the clause then simply doesn't match. */
    public static Optional<SemVer> parse(@Nullable String input) {
        if (input == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(input.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SemVer(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(SemVer other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(patch, other.patch);
        if (result != 0) {
            return result;
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    private static int comparePrerelease(@Nullable String left, @Nullable String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;   // release > pre-release
        }
        if (right == null) {
            return -1;
        }
        String[] leftIds = left.split("\\.");
        String[] rightIds = right.split("\\.");
        for (int i = 0; i < Math.min(leftIds.length, rightIds.length); i++) {
            int result = compareIdentifier(leftIds[i], rightIds[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(leftIds.length, rightIds.length);
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        }
        if (leftNumeric) {
            return -1;  // numeric identifiers rank below alphanumeric
        }
        if (rightNumeric) {
            return 1;
        }
        return left.compareTo(right);
    }
}
