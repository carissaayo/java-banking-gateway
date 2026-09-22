package com.yussufajao.gateway.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorrelationIdTest {

	@Test
	void acceptsStrictIdentifiers() {
		assertThat(CorrelationId.resolve(List.of("corr-1234"))).isEqualTo("corr-1234");
		assertThat(CorrelationId.resolve(List.of("550e8400-e29b-41d4-a716-446655440000")))
				.isEqualTo("550e8400-e29b-41d4-a716-446655440000");
	}

	@Test
	void rejectsLogInjectionAndUnboundedValues() {
		assertThat(CorrelationId.resolve(List.of("abc\nInjected"))).isNotEqualTo("abc\nInjected");
		assertThat(CorrelationId.resolve(List.of("short"))).isNotEqualTo("short");
		assertThat(CorrelationId.resolve(List.of("a".repeat(129)))).hasSize(36);
		assertThat(CorrelationId.resolve(List.of("ok", "also-ok"))).hasSize(36);
		assertThat(CorrelationId.resolve(null)).hasSize(36);
	}
}
