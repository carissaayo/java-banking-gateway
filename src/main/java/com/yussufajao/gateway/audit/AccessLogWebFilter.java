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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogWebFilter implements WebFilter {

	private static final Logger ACCESS = LoggerFactory.getLogger("gateway.access");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long started = System.nanoTime();
		return chain.filter(exchange)
				.doOnEach(signal -> {
					if (signal.getType() == SignalType.ON_COMPLETE || signal.getType() == SignalType.ON_ERROR) {
						Authentication authentication = null;
						Object stored = signal.getContextView().getOrDefault(SecurityContext.class, null);
						if (stored instanceof SecurityContext securityContext) {
							authentication = securityContext.getAuthentication();
						}
						write(exchange, started, authentication);
					}
				});
	}

	private static void write(ServerWebExchange exchange, long startedNanos, Authentication authentication) {
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
		MDC.put("clientId", AuthenticatedClient.clientId(authentication));
		MDC.put("subject", AuthenticatedClient.subject(authentication));
		MDC.put("rateLimitDecision", stringAttr(exchange, GatewayAttributes.RATE_LIMIT_DECISION));
		
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
