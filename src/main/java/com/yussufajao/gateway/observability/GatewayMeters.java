package com.yussufajao.gateway.observability;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMeters {

	public static final String DECISION_ALLOWED = "allowed";
	public static final String DECISION_EXCEEDED = "exceeded";
	public static final String DECISION_FAIL_CLOSED = "fail-closed";
	public static final String DECISION_FAIL_OPEN = "fail-open";

	private final MeterRegistry registry;

	public GatewayMeters(MeterRegistry registry) {
		this.registry = registry;
	}

	public void rateLimit(String policyId, String decision) {
		Counter.builder("gateway.rate_limit")
				.description("Rate-limit outcomes by catalog policy")
				.tag("policy", safe(policyId))
				.tag("decision", safe(decision))
				.register(registry)
				.increment();
	}

	public void authFailure(GatewayErrorCode code) {
		Counter.builder("gateway.auth.failures")
				.description("Authentication and authorization failures")
				.tag("code", code.slug())
				.register(registry)
				.increment();
	}

	public void policyRejection(GatewayErrorCode code) {
		Counter.builder("gateway.policy.rejections")
				.description("Gateway policy rejections before or instead of proxying")
				.tag("code", code.slug())
				.register(registry)
				.increment();
	}

	public void mappedFailure(GatewayErrorCode code) {
		switch (code) {
			case INVALID_CREDENTIALS, INSUFFICIENT_SCOPE -> authFailure(code);
			case UPSTREAM_TIMEOUT, UPSTREAM_UNAVAILABLE, UPSTREAM_ERROR, CIRCUIT_OPEN -> upstreamFailure(code);
			case INTERNAL_ERROR -> internalError();
			default -> policyRejection(code);
		}
	}

	public void upstreamFailure(GatewayErrorCode code) {
		Counter.builder("gateway.upstream.failures")
				.description("Upstream timeouts, unavailability, HTTP 5xx, and open circuits")
				.tag("code", code.slug())
				.register(registry)
				.increment();
	}

	public void internalError() {
		Counter.builder("gateway.internal.errors")
				.description("Unclassified gateway failures")
				.register(registry)
				.increment();
	}

	private static String safe(String value) {
		return value == null || value.isBlank() ? "none" : value;
	}
}
