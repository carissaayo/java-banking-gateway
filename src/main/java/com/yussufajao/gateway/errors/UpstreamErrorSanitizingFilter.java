package com.yussufajao.gateway.errors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.yussufajao.gateway.correlation.CorrelationId;
import com.yussufajao.gateway.correlation.GatewayAttributes;
import com.yussufajao.gateway.routing.GatewayHeaders;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UpstreamErrorSanitizingFilter implements GlobalFilter, Ordered {

	private final GatewayProblemMapper mapper;
	private final JsonMapper jsonMapper;

	public UpstreamErrorSanitizingFilter(GatewayProblemMapper mapper, JsonMapper jsonMapper) {
		this.mapper = mapper;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				HttpStatusCode status = getStatusCode();
				if (status == null || !status.is5xxServerError()) {
					return super.writeWith(body);
				}
				exchange.getAttributes().put(GatewayAttributes.UPSTREAM_OUTCOME, "upstream_error");
				return DataBufferUtils.join(body)
						.defaultIfEmpty(bufferFactory().wrap(new byte[0]))
						.flatMap(buffer -> {
							DataBufferUtils.release(buffer);
							return super.writeWith(Mono.just(problemBuffer(exchange, status.value())));
						});
			}

			@Override
			public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
				return writeWith(Flux.from(body).flatMap(Flux::from));
			}
		};
		return chain.filter(exchange.mutate().response(decorated).build());
	}

	private DataBuffer problemBuffer(ServerWebExchange exchange, int status) {
		String correlationId = correlationId(exchange);
		String instance = exchange.getRequest().getURI().getRawPath();
		GatewayProblem problem = mapper.upstreamError(status, instance, correlationId);
		byte[] bytes;
		try {
			bytes = jsonMapper.writeValueAsBytes(problem);
		}
		catch (JacksonException ex) {
			bytes = "{\"title\":\"Upstream error\",\"status\":500}".getBytes();
		}
		ServerHttpResponse response = exchange.getResponse();
		HttpHeaders headers = response.getHeaders();
		headers.remove(HttpHeaders.TRANSFER_ENCODING);
		headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		headers.setContentLength(bytes.length);
		headers.set(GatewayHeaders.CORRELATION_ID, correlationId);
		return response.bufferFactory().wrap(bytes);
	}

	private static String correlationId(ServerWebExchange exchange) {
		Object attribute = exchange.getAttributes().get(GatewayAttributes.CORRELATION_ID);
		if (attribute instanceof String value && !value.isBlank()) {
			return value;
		}
		return CorrelationId.generate();
	}

	@Override
	public int getOrder() {
		return -2;
	}
}
