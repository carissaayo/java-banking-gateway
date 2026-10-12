package com.yussufajao.gateway.hardening;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(70)
public class RequestSizeWebFilter implements WebFilter{

    private final RequestSizeCatalog catalog = new RequestSizeCatalog();
    private final GatewayProblemHttpWriter problemWriter;

    public RequestSizeWebFilter(GatewayProblemHttpWriter problemWriter){
        this.problemWriter = problemWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        var method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getRawPath();
        return catalog.maxBodyBytes(method,path)
                .map(maxBytes -> enforce(exchange, chain, maxBytes))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Mono<Void> enforce(ServerWebExchange exchange, WebFilterChain chain, long maxBytes){
        String raw = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (raw == null || raw.isBlank()){
            return chain.filter(exchange);
        }
        long length;
        try{
            length = Long.parseLong(raw.trim());
        }
        catch(NumberFormatException ex){
            return tooLarge(exchange);
        }
        if(length < 0 || length > maxBytes){
            return tooLarge(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> tooLarge(ServerWebExchange exchange){
        return problemWriter.write(
            exchange,
            GatewayErrorCode.REQUEST_TOO_LARGE,
            HttpStatus.PAYLOAD_TOO_LARGE);
    }
}