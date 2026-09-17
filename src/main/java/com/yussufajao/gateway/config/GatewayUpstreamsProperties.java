package com.yussufajao.gateway.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.upstreams")
public record GatewayUpstreamsProperties(
		@NotNull URI ledger,
		@NotNull URI customer
) {

	public GatewayUpstreamsProperties {
		requireHttpUri("gateway.upstreams.ledger", ledger);
		requireHttpUri("gateway.upstreams.customer", customer);
	}

	private static void requireHttpUri(String name, URI uri) {
		if (uri == null) {
			throw new IllegalArgumentException(name + " must be set");
		}
		String scheme = uri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new IllegalArgumentException(name + " must be an http or https URI");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException(name + " must include a host");
		}
	}
}
