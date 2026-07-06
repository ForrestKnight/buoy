package io.github.forrestknight.buoy.config;

import io.github.forrestknight.buoy.config.ApiKeyAuthentication.ApiKeyPrincipal;
import io.github.forrestknight.buoy.domain.ApiKey;
import io.github.forrestknight.buoy.domain.ApiKeyKind;
import io.github.forrestknight.buoy.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code buoy_srv_} / {@code buoy_adm_} tokens from the Authorization
 * header (with or without a {@code Bearer} prefix). Unknown or revoked tokens are
 * left unauthenticated and fall through to the 401 at authorization. JWTs never
 * reach this code path — and the bearer resolver in {@link SecurityConfig} makes
 * the resource server ignore {@code buoy_}-prefixed tokens symmetrically.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyService.authenticate(token).ifPresent(key -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new ApiKeyAuthentication(principalOf(key), List.of(authorityOf(key))));
            });
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return null;
        }
        String candidate = header.startsWith("Bearer ") ? header.substring(7) : header;
        return candidate.startsWith("buoy_") ? candidate : null;
    }

    private ApiKeyPrincipal principalOf(ApiKey key) {
        return new ApiKeyPrincipal(key.getId(), key.getKind(), key.getName(),
                key.getEnvironment().getId(), key.getEnvironment().getKey(),
                key.getEnvironment().getProject().getId(), key.getEnvironment().getProject().getKey());
    }

    private SimpleGrantedAuthority authorityOf(ApiKey key) {
        return new SimpleGrantedAuthority(
                key.getKind() == ApiKeyKind.SERVER_SDK ? "ROLE_SDK" : "ROLE_ADMIN_KEY");
    }
}
