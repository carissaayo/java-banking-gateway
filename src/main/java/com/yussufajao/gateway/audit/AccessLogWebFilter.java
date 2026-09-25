package com.yussufajao.gateway.audit;

import com.yussufajao.gateway.correlation.GatewayAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogWebFilter implements WebFilter {

	private static final Logger ACCESS = LoggerFactory.getLogger("gateway.access");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long started = System.nanoTime();
		return chain.filter(exchange)
				.doFinally(signal -> write(exchange, started));
	}

	private static void write(ServerWebExchange exchange, long startedNanos) {
		HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
		int status = statusCode != null ? statusCode.value() : 0;
		long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
		String method = exchange.getRequest().getMethod().name();
		String path = SafeRequestPath.of(exchange.getRequest().getURI());
		String correlationId = stringAttr(exchange, GatewayAttributes.CORRELATION_ID);
		String traceId = stringAttr(exchange, GatewayAttributes.TRACE_ID);
		String routeId = routeId(exchange);
		String outcome = outcome(exchange, status);

		MDC.put("correlationId", correlationId);
		MDC.put("traceId", traceId);
		MDC.put("routeId", routeId);
		MDC.put("httpMethod", method);
		MDC.put("httpPath", path);
		MDC.put("httpStatus", Integer.toString(status));
		MDC.put("durationMs", Long.toString(durationMs));
		MDC.put("upstreamOutcome", outcome);
		try {
			ACCESS.info("access");
		}
		finally {
			MDC.clear();
		}
	}

	private static String routeId(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		return route != null ? route.getId() : "none";
	}

	private static String outcome(ServerWebExchange exchange, int status) {
		String stored = stringAttr(exchange, GatewayAttributes.UPSTREAM_OUTCOME);
		if (!stored.isBlank()) {
			return stored;
		}
		if (status >= 500) {
			return "error";
		}
		if (status >= 400) {
			return "rejected";
		}
		return "success";
	}

	private static String stringAttr(ServerWebExchange exchange, String key) {
		Object value = exchange.getAttributes().get(key);
		return value instanceof String text ? text : "";
	}
}
