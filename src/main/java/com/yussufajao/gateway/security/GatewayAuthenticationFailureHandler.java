package com.yussufajao.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

public class GatewayAuthenticationFailureHandler implements ServerAuthenticationEntryPoint{
    
    @Override
    public Mono<Void> commence( ServerWebExchange exchange, AuthenticationException exception){
         // Build your existing GatewayProblem here.
        // type = invalid-credentials
        // status = 401
        // constant detail
        // correlationId = existing correlation ID


        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse()
                .getHeaders()
                .setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        return Mono.empty();
    }
}
