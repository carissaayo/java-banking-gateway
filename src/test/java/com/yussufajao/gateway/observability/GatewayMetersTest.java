package com.yussufajao.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GatewayMetersTest {

	@Test
	void mappedFailuresUseLowCardinalityCodeTags() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		GatewayMeters meters = new GatewayMeters(registry);

		meters.mappedFailure(GatewayErrorCode.INVALID_CREDENTIALS);
		meters.mappedFailure(GatewayErrorCode.UPSTREAM_TIMEOUT);
		meters.mappedFailure(GatewayErrorCode.CIRCUIT_OPEN);
		meters.mappedFailure(GatewayErrorCode.MISSING_IDEMPOTENCY_KEY);
		meters.mappedFailure(GatewayErrorCode.INTERNAL_ERROR);

		assertThat(registry.counter("gateway.auth.failures", "code", "invalid-credentials").count())
				.isEqualTo(1.0);
		assertThat(registry.counter("gateway.upstream.failures", "code", "upstream-timeout").count())
				.isEqualTo(1.0);
		assertThat(registry.counter("gateway.upstream.failures", "code", "circuit-open").count())
				.isEqualTo(1.0);
		assertThat(registry.counter("gateway.policy.rejections", "code", "missing-idempotency-key").count())
				.isEqualTo(1.0);
		assertThat(registry.counter("gateway.internal.errors").count()).isEqualTo(1.0);
	}
}
