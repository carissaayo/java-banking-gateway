package com.yussufajao.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayCorsPropertiesTest {

	@Test
	void rejectsWildcardOrigin() {
		assertThatThrownBy(() -> new GatewayCorsProperties(List.of("*")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not contain *");
	}

	@Test
	void nullOriginsBecomeEmptyAllowlist() {
		assertThat(new GatewayCorsProperties(null).allowedOrigins()).isEmpty();
	}
}
