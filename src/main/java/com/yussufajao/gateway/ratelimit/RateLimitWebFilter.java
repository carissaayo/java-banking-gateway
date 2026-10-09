package com.yussufajao.gateway.ratelimit;

import com.yussufajao.gateway.audit.AuthenticatedClient;
import com.yussufajao.gateway.correlation.GatewayAttributes;
import com.yussufajao.gateway.errors.GatewayErrorCode;
import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import com.yussufajao.gateway.routing.GatewayHeaders;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(100)
public class RateLimitWebFilter implements WebFilter {

	private static final Logger LOG = LoggerFactory.getLogger(RateLimitWebFilter.class);

	private final RateLimitPolicyCatalog catalog = new RateLimitPolicyCatalog();
	private final RateLimiter rateLimiter;
	private final GatewayProblemHttpWriter problemWriter;

	public RateLimitWebFilter(RateLimiter rateLimiter, GatewayProblemHttpWriter problemWriter) {
		this.rateLimiter = rateLimiter;
		this.problemWriter = problemWriter;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		var method = exchange.getRequest().getMethod();
		String path = exchange.getRequest().getURI().getRawPath();
		return catalog.policyFor(method, path)
				.map(policy -> enforce(exchange, chain, policy))
				.orElseGet(() -> chain.filter(exchange));
	}

	private Mono<Void> enforce(ServerWebExchange exchange, WebFilterChain chain, RateLimitPolicy policy) {
		return ReactiveSecurityContextHolder.getContext()
				.map(context -> Optional.ofNullable(context.getAuthentication()))
				.defaultIfEmpty(Optional.empty())
				.flatMap(authentication -> consume(exchange, chain, policy, authentication.orElse(null)));
	}

	private Mono<Void> consume(
			ServerWebExchange exchange,
			WebFilterChain chain,
			RateLimitPolicy policy,
			Authentication authentication) {
		String key = RateLimitKey.of(
				policy.id(),
				AuthenticatedClient.clientId(authentication),
				AuthenticatedClient.subject(authentication));
		return rateLimiter.consume(key, policy)
				.map(Optional::of)
				.onErrorResume(error -> recoverFromRedis(exchange, policy, error))
				.flatMap(decision -> {
					if (decision.isEmpty()) {
						return Mono.empty();
					}
					return applyDecision(exchange, chain, decision.get());
				});
	}

	private Mono<Optional<RateLimitDecision>> recoverFromRedis(
			ServerWebExchange exchange,
			RateLimitPolicy policy,
			Throwable error) {
		if (policy.failClosed()) {
			LOG.error("redis rate limiter failed; fail-closed policy={}", policy.id(), error);
			exchange.getAttributes().put(GatewayAttributes.RATE_LIMIT_DECISION, "unavailable");
			return problemWriter.write(
							exchange,
							GatewayErrorCode.RATE_LIMITER_UNAVAILABLE,
							HttpStatus.SERVICE_UNAVAILABLE)
					.thenReturn(Optional.empty());
		}
		LOG.warn("redis rate limiter failed; fail-open policy={}", policy.id(), error);
		exchange.getAttributes().put(GatewayAttributes.RATE_LIMIT_DECISION, "degraded-allow");
		return Mono.just(Optional.of(RateLimitDecision.allow(policy.burstCapacity(), policy)));
	}

	private Mono<Void> applyDecision(
			ServerWebExchange exchange,
			WebFilterChain chain,
			RateLimitDecision decision) {
		headers(exchange, decision);
		if (!decision.allowed()) {
			exchange.getAttributes().put(GatewayAttributes.RATE_LIMIT_DECISION, "exceeded");
			return problemWriter.write(
					exchange,
					GatewayErrorCode.RATE_LIMIT_EXCEEDED,
					HttpStatus.TOO_MANY_REQUESTS);
		}
		exchange.getAttributes().put(GatewayAttributes.RATE_LIMIT_DECISION, "allowed");
		return chain.filter(exchange);
	}

	private static void headers(ServerWebExchange exchange, RateLimitDecision decision) {
		var headers = exchange.getResponse().getHeaders();
		setHeader(headers, GatewayHeaders.RATE_LIMIT_LIMIT, Integer.toString(decision.limit()));
		setHeader(headers, GatewayHeaders.RATE_LIMIT_REMAINING, Integer.toString(decision.remaining()));
		setHeader(headers, GatewayHeaders.RATE_LIMIT_REPLENISH_RATE, Integer.toString(decision.replenishRate()));
	}

	private static void setHeader(HttpHeaders headers, String name, String value) {
		try {
			headers.set(name, value);
		}
		catch (UnsupportedOperationException ignored) {
			// MockServerHttpResponse can expose read-only headers; Netty responses are writable.
		}
	}
}
