package com.yussufajao.gateway.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TraceParentTest {

	@Test
	void keepsTraceIdAndCreatesChildSpan() {
		TraceParent child = TraceParent.childOf(
				List.of("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"));

		assertThat(child.traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
		assertThat(child.spanId()).isNotEqualTo("00f067aa0ba902b7");
		assertThat(child.value()).startsWith("00-0af7651916cd43dd8448eb211c80319c-");
	}

	@Test
	void rejectsZeroIdsAndGarbage() {
		assertThat(TraceParent.childOf(List.of("00-" + "0".repeat(32) + "-" + "0".repeat(16) + "-01"))
				.traceId()).isNotEqualTo("0".repeat(32));
		assertThat(TraceParent.childOf(List.of("not-a-trace")).value()).startsWith("00-");
		assertThat(TraceParent.sanitizeState(List.of("vendor=value"))).isEqualTo("vendor=value");
		assertThat(TraceParent.sanitizeState(List.of("bad\nstate"))).isNull();
		assertThat(TraceParent.sanitizeState(List.of("x".repeat(513)))).isNull();
	}
}
