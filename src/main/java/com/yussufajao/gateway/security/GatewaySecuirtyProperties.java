public package com.yussufajao.gateway.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
		@NotNull URI issuerUri,
		@NotBlank String audience
) {

	public GatewaySecurityProperties {
		requireHttpUri("gateway.security.issuer-uri", issuerUri);
		if (audience.isBlank()) {
			throw new IllegalArgumentException("gateway.security.audience must be set");
		}
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
} {
    
}
