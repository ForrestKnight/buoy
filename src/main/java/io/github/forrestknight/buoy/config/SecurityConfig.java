package io.github.forrestknight.buoy.config;

import io.github.forrestknight.buoy.service.ApiKeyAuthenticationCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Two token families, one chain:
 * <ul>
 *   <li>{@code buoy_srv_}/{@code buoy_adm_} API keys — authenticated by
 *       {@link ApiKeyAuthenticationFilter}, invisible to the JWT resource server.</li>
 *   <li>Self-issued HMAC JWTs from {@code POST /auth/login} — verified by the
 *       resource server; role/ownership checks happen per-project via
 *       {@code @PreAuthorize("@projectAccess...")} method security.</li>
 * </ul>
 * SDK keys reach only the evaluation surface; the admin surface takes users (JWT)
 * or admin keys. {@code /api/v1/stream} is matched here ahead of time as the
 * authorization contract for the planned SSE endpoint (see the roadmap issue);
 * the endpoint itself does not exist yet.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticationCache authenticationCache)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(authenticationCache),
                        UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/evaluate/**", "/api/v1/stream").hasRole("SDK")
                        .requestMatchers("/api/v1/**").hasAnyRole("USER", "ADMIN_KEY")
                        .anyRequest().denyAll())
                .build();
    }

    /** The resource server must not try to parse API keys as JWTs. */
    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String token = delegate.resolve(request);
            return token != null && token.startsWith("buoy_") ? null : token;
        };
    }

    /** Every valid JWT is a console user; instance admins carry an extra role. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if (Boolean.TRUE.equals(jwt.getClaim("instanceAdmin"))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_INSTANCE_ADMIN"));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
