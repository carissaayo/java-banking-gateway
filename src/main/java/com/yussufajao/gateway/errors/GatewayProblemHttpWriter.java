package com.yussufajao.gateway.errors;

import com.yussufajao.gateway.correlation.CorrelationId;
import com.yussufajao.gateway.correlation.GatewayAttributes;
import com.yussufajao.gateway.routing.GatewayHeaders;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GatewayProblemHttpWriter {
    private final GatewayProblemMapper mapper;
    private final JsonMapper jsonMapper;

    public GatewayProblemHttpWriter(GatewayProblemMapper mapper, JsonMapper jsonMapper){
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, GatewayErrorCode code, HttpStatus status){
        String correlationId = correlationId(exchange);
        String instance = exchange.getRequest().getURI().getRawPath();
        GatewayProblem problem = mapper.of(code, instance, correlationId);
        byte[] bytes;
        try{
            bytes = jsonMapper.writeValuesAsBytes(problem);
        }
        catch (JacksonException ex){
            bytes = "{\"title\":\"Internal error\",\"status\":500}".getBytes();
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setContentLength(bytes.length);
        response.getHeaders().set(GatewayHeaders.CORRELATION_ID, correlationId);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
        }

    private static String correlationId(ServerWebExchange exchange){
        Object attribute = exchange.getAttributes().get(GatewayAttributes.CORRELATION_ID);
        if(attribute instanceof String value && !value.isBlank()){
            return value;
        }
        return CorrelationId.generate();
    }
}
