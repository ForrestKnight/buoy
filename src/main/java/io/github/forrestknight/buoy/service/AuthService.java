package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.config.BuoySecurityProperties;
import io.github.forrestknight.buoy.domain.AppUser;
import io.github.forrestknight.buoy.persistence.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final BuoySecurityProperties properties;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, BuoySecurityProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    @Transactional(readOnly = true)
    public IssuedToken login(String username, String password) {
        AppUser user = userRepository.findByUsername(username)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwtTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("instanceAdmin", user.isInstanceAdmin())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
