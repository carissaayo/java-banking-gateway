package com.yussufajao.gateway.resilience;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import com.yussufajao.gateway.routing.GatewayHeaders;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(90)
public class IdempotencyKeyWebFilter implements WebFilter{

    private final IdempotencyKeyCatalog catalog = new IdempotencyKeyCatalog();
    private final GatewayProblemHttpWriter problemWriter;

    public IdempotencyKeyWebFilter(GatewayProblemHttpWriter problemWriter){
        this.problemWriter = problemWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        var method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getRawPath();
        if(!catalog.required(method, path)){
            return chain.filter(exchange);
        }
        String key = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()){
            return problemWriter.write(
                exchange,
                GatewayErrorCode.MISSING_IDEMPOTENCY_KEY,
                HttpStatus.BAD_REQUEST
            );
        }
        return chain.filter(exchange);
    }
}