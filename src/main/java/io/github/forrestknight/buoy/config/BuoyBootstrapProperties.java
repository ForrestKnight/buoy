package io.github.forrestknight.buoy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * First-admin bootstrap. Applied only when no users exist yet. If password is
 * unset, a random one is generated and printed to the log once, Jenkins-style.
 */
@ConfigurationProperties(prefix = "buoy.bootstrap")
public record BuoyBootstrapProperties(@DefaultValue("admin") String username, String password) {
}
