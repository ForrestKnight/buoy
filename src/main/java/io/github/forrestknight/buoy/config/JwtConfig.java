package io.github.forrestknight.buoy.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Self-issued HMAC JWTs: the admin API is both the authorization server (login
 * issues tokens) and the resource server (requests verify them) — one shared key,
 * no OIDC ceremony. Deliberately boring for a self-hosted single node.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    SecretKey jwtSecretKey(BuoySecurityProperties properties) {
        if (properties.jwtSecret() != null && !properties.jwtSecret().isBlank()) {
            byte[] bytes = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("buoy.security.jwt-secret must be at least 32 bytes");
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        }
        log.warn("buoy.security.jwt-secret is not set; using a random secret — "
                + "issued tokens will not survive a restart");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return new SecretKeySpec(random, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
