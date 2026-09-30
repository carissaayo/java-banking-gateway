package com.yussufajao.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class GatewayAccessDeniedHandler
        implements ServerAccessDeniedHandler {

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException exception
    ) {
        // Build your existing GatewayProblem here.
        // type = insufficient-scope
        // status = 403
        // constant detail
        // correlationId = existing correlation ID

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);

        return Mono.empty();
    }
}