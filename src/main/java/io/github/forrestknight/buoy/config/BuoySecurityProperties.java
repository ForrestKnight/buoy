package io.github.forrestknight.buoy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param jwtSecret HMAC-SHA256 signing secret (>= 32 bytes). When unset, a random
 *                  secret is generated at startup and tokens do not survive restarts.
 * @param jwtTtl    Lifetime of issued admin tokens.
 */
@ConfigurationProperties(prefix = "buoy.security")
public record BuoySecurityProperties(String jwtSecret, @DefaultValue("12h") Duration jwtTtl) {
}
