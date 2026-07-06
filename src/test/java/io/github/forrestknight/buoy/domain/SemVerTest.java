package io.github.forrestknight.buoy.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemVerTest {

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource({
            // left, right, expected sign of compareTo
            "1.0.0,          1.0.0,           0",
            "2.0.0,          1.9.9,           1",
            "1.10.0,         1.9.0,           1",
            "1.0.10,         1.0.9,           1",
            "0.9.0,          1.0.0,          -1",
            "1.0.0-alpha,    1.0.0,          -1",
            "1.0.0,          1.0.0-rc.1,      1",
            "1.0.0-alpha,    1.0.0-alpha.1,  -1",
            "1.0.0-alpha.1,  1.0.0-alpha.2,  -1",
            "1.0.0-alpha.2,  1.0.0-alpha.10, -1",
            "1.0.0-alpha.1,  1.0.0-beta,     -1",
            "1.0.0-1,        1.0.0-alpha,    -1",
            "1.0.0-rc.1,     1.0.0-rc.1,      0",
    })
    void precedence(String left, String right, int expectedSign) {
        SemVer a = SemVer.parse(left).orElseThrow();
        SemVer b = SemVer.parse(right).orElseThrow();
        assertThat(Integer.signum(a.compareTo(b))).isEqualTo(expectedSign);
        assertThat(Integer.signum(b.compareTo(a))).isEqualTo(-expectedSign);
    }

    @ParameterizedTest(name = "invalid: {0}")
    @CsvSource(value = {"1.0", "1", "v1.0.0", "1.0.0.0", "not-a-version", "1.0.x", "''"}, nullValues = "null")
    void invalidInputsParseEmpty(String input) {
        assertThat(SemVer.parse(input)).isEmpty();
    }

    @Test
    void buildMetadataIsIgnored() {
        SemVer withBuild = SemVer.parse("1.0.0+build.42").orElseThrow();
        SemVer without = SemVer.parse("1.0.0").orElseThrow();
        assertThat(withBuild.compareTo(without)).isZero();
    }

    @Test
    void parseExtractsComponents() {
        SemVer version = SemVer.parse("2.10.3-rc.1+sha.5114f85").orElseThrow();
        assertThat(version.major()).isEqualTo(2);
        assertThat(version.minor()).isEqualTo(10);
        assertThat(version.patch()).isEqualTo(3);
        assertThat(version.prerelease()).isEqualTo("rc.1");
    }
}
