package com.yussufajao.gateway.security;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class BearerTokenQueryRejector implements WebFilter{

    private final GatewayAuthenticationFailureHandler authenticationFailureHandler;

    public BearerTokenQueryRejector(GatewayAuthenticationFailureHandler authenticationFailureHandler){
        this.authenticationFailureHandler = authenticationFailureHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        if (hasTokenInQuery(exchange.getRequest().getQueryParams())){
            return authenticationFailureHandler.write(
                exchange,
                GatewayErrorCode.INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED
            );
        }
        return chain.filter(exchange);
    }

    static boolean hasTokenInQuery(MultiValueMap<String,String> query){
        return query.containsKey("access_token") || query.containsKey("id_token");
    }
    
}
