package com.yussufajao.gateway.correlation;

import com.yussufajao.gateway.routing.GatewayHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String correlationId = CorrelationId.resolve(
				exchange.getRequest().getHeaders().get(GatewayHeaders.CORRELATION_ID));
		TraceParent traceParent = TraceParent.childOf(
				exchange.getRequest().getHeaders().get(GatewayHeaders.TRACEPARENT));
		String traceState = TraceParent.sanitizeState(
				exchange.getRequest().getHeaders().get(GatewayHeaders.TRACESTATE));

		ServerHttpRequest request = exchange.getRequest().mutate()
				.headers(headers -> {
					headers.set(GatewayHeaders.CORRELATION_ID, correlationId);
					headers.set(GatewayHeaders.TRACEPARENT, traceParent.value());
					if (traceState != null) {
						headers.set(GatewayHeaders.TRACESTATE, traceState);
					}
					else {
						headers.remove(GatewayHeaders.TRACESTATE);
					}
				})
				.build();

		exchange.getResponse().getHeaders().set(GatewayHeaders.CORRELATION_ID, correlationId);
		exchange.getAttributes().put(GatewayAttributes.CORRELATION_ID, correlationId);
		exchange.getAttributes().put(GatewayAttributes.TRACE_ID, traceParent.traceId());

		return chain.filter(exchange.mutate().request(request).build())
				.contextWrite(Context.of(
						GatewayAttributes.CORRELATION_ID, correlationId,
						GatewayAttributes.TRACE_ID, traceParent.traceId()));
	}
}
