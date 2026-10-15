package com.yussufajao.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class GatewaySecurityPropertiesTest {

	@Test
	void blankJwkSetUriMeansIssuerDiscovery() {
		GatewaySecurityProperties properties = new GatewaySecurityProperties(
				URI.create("http://127.0.0.1:8180/realms/banking"),
				"banking-gateway",
				"  ");
		assertThat(properties.jwkSetUri()).isNull();
	}

	@Test
	void jwkSetUriMustBeHttp() {
		assertThatThrownBy(() -> new GatewaySecurityProperties(
						URI.create("http://127.0.0.1:8180/realms/banking"),
						"banking-gateway",
						"ftp://jwks.example"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("jwk-set-uri");
	}
}
