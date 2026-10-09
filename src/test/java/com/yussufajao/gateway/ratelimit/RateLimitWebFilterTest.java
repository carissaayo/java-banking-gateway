package com.yussufajao.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

class RateLimitWebFilterTest {

	@Test
	void healthSkipsLimiter() {
		RateLimiter limiter = (key, policy) -> Mono.error(new AssertionError("should not consume"));
		GatewayProblemHttpWriter writer = mock(GatewayProblemHttpWriter.class);
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));
		filter.filter(exchange, ex -> Mono.empty()).block();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void exceededWritesTooManyRequests() {
		RateLimiter limiter = (key, policy) -> Mono.just(RateLimitDecision.deny(0, policy));
		GatewayProblemHttpWriter writer = mockWriter();
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/ledger/transfers"));
		filter.filter(exchange, ex -> Mono.empty())
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
				.block();
		verify(writer).write(eq(exchange), eq(GatewayErrorCode.RATE_LIMIT_EXCEEDED), eq(HttpStatus.TOO_MANY_REQUESTS));
	}

	@Test
	void redisFailureFailClosedWrites503() {
		RateLimiter limiter = (key, policy) -> Mono.error(new IllegalStateException("redis down"));
		GatewayProblemHttpWriter writer = mockWriter();
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/ledger/transfers"));
		filter.filter(exchange, ex -> Mono.empty())
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
				.block();
		verify(writer).write(
				eq(exchange),
				eq(GatewayErrorCode.RATE_LIMITER_UNAVAILABLE),
				eq(HttpStatus.SERVICE_UNAVAILABLE));
	}

	@Test
	void redisFailureFailOpenContinues() {
		RateLimiter limiter = (key, policy) -> Mono.error(new IllegalStateException("redis down"));
		GatewayProblemHttpWriter writer = mock(GatewayProblemHttpWriter.class);
		var filter = new RateLimitWebFilter(limiter, writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/transactions/txn-1"));
		boolean[] continued = {false};
		filter.filter(exchange, ex -> {
					continued[0] = true;
					return Mono.empty();
				})
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
				.block();
		assertThat(continued[0]).isTrue();
	}

	private static GatewayProblemHttpWriter mockWriter() {
		GatewayProblemHttpWriter writer = mock(GatewayProblemHttpWriter.class);
		when(writer.write(any(), any(), any())).thenReturn(Mono.empty());
		return writer;
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
