package io.github.forrestknight.buoy.config;

import io.github.forrestknight.buoy.domain.ApiKeyKind;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    /** Everything authorization decisions need, resolved once at authentication time. */
    public record ApiKeyPrincipal(Long keyId, ApiKeyKind kind, String name,
                                  Long environmentId, String environmentKey,
                                  Long projectId, String projectKey) {
    }

    private final ApiKeyPrincipal principal;

    public ApiKeyAuthentication(ApiKeyPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public ApiKeyPrincipal getPrincipal() {
        return principal;
    }
}
