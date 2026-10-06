package com.yussufajao.gateway.security;

import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedReactiveAuthorizationManager;
import org.springframework.security.authorization.AuthorityReactiveAuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RouteScopeAuthorizationManager  implements ReactiveAuthorizationManager<AuthorizationContext>{
    
    private static final String SCOPE_PREFIX = "SCOPE_";

    private final RouteScopeCatalog catalog = new RouteScopeCatalog();

    @Override
    public Mono<AuthorizationResult> authorize(
        Mono<Authentication> authentication,
        AuthorizationContet context
    ){
        HttpMethod method = context.getExchange().getRequest().getMethod();
        String path = context.getExchange().getRequest().getURI().getRawPath();
        Optional<String> required =catalog.requiredScope(method, path);

        if (required.isEmpty()){
            return AuthenticatedReactiveAuthorizationManager.authenticated()
            .authorize(authentication, context);
        }

        String authority = SCOPE_PREFIX + required.get();
        return AuthorityReactiveAuthorizationManager.<AuthorizationContext>hasAuthority(authority)
        .authorize(authentication, context);
    }
}
