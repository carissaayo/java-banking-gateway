package com.yussufajao.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class GatewayUpstreamsPropertiesTest {

	@Test
	void acceptsHttpAndHttpsUpstreams() {
		var properties = new GatewayUpstreamsProperties(
				URI.create("http://127.0.0.1:8081"),
				URI.create("https://customer.internal.example"));

		assertThat(properties.ledger()).hasHost("127.0.0.1");
		assertThat(properties.customer()).hasScheme("https");
	}

	@Test
	void rejectsMissingHost() {
		assertThatThrownBy(() -> new GatewayUpstreamsProperties(
				URI.create("http:///ledger"),
				URI.create("http://127.0.0.1:8082")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("gateway.upstreams.ledger");
	}

	@Test
	void rejectsNonHttpScheme() {
		assertThatThrownBy(() -> new GatewayUpstreamsProperties(
				URI.create("ftp://files.example"),
				URI.create("http://127.0.0.1:8082")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("http or https");
	}
}
