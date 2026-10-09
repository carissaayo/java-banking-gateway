package com.yussufajao.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import com.yussufajao.gateway.errors.GatewayProblemMapper;
import com.yussufajao.gateway.routing.GatewayHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class RateLimitWebFilterTest {

	private final GatewayProblemHttpWriter writer =
			new GatewayProblemHttpWriter(new GatewayProblemMapper(), JsonMapper.builder().build());

	@Test
	void healthSkipsLimiter() {
		RateLimiter limiter = (key, policy) -> Mono.error(new AssertionError("should not consume"));
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));
		filter.filter(exchange, (ex) -> Mono.empty()).block();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void exceededReturns429() {
		RateLimiter limiter = (key, policy) -> Mono.just(RateLimitDecision.deny(0, policy));
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(
				MockServerHttpRequest.post("/api/ledger/transfers"));
		filter.filter(exchange, (ex) -> Mono.empty())
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
				.block();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exchange.getResponse().getHeaders().getFirst(GatewayHeaders.RATE_LIMIT_LIMIT))
				.isEqualTo("4");
	}

	@Test
	void redisFailureFailClosedReturns503() {
		RateLimiter limiter = (key, policy) -> Mono.error(new IllegalStateException("redis down"));
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(
				MockServerHttpRequest.post("/api/ledger/transfers"));
		filter.filter(exchange, (ex) -> Mono.empty())
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
				.block();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void redisFailureFailOpenContinues() {
		RateLimiter limiter = (key, policy) -> Mono.error(new IllegalStateException("redis down"));
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/transactions/txn-1"));
		boolean[] continued = {false};
		filter.filter(exchange, (ex) -> {
					continued[0] = true;
					return Mono.empty();
				})
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
				.block();
		assertThat(continued[0]).isTrue();
	}

	private static JwtAuthenticationToken jwtAuth() {
		Jwt jwt = Jwt.withTokenValue("t")
				.header("alg", "none")
				.subject("sub-1")
				.claim("azp", "customer-app")
				.issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
				.expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
				.build();
		return new JwtAuthenticationToken(jwt, List.of());
	}
}