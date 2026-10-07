package com.yussufajao.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	private final GatewayAuthenticationFailureHandler authenticationFailureHandler;
	private final GatewayAccessDeniedHandler accessDeniedHandler;

	public SecurityConfig(
			GatewayAuthenticationFailureHandler authenticationFailureHandler,
			GatewayAccessDeniedHandler accessDeniedHandler,
			RouteScopeAuthorizationManager routeScopes
		) {
		this.authenticationFailureHandler = authenticationFailureHandler;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Bean
	SecurityWebFilterChain securityWebFilterChain(
		ServerHttpSecurity http,
		 ReactiveJwtDecoder jwtDecoder,
		 RouteScopeAuthorizationManager routeScopes
		) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(
								HttpMethod.GET,
								"/actuator/health",
								"/actuator/health/liveness",
								"/actuator/health/readiness")
						.permitAll()
						.pathMatchers("/api/**").access(routeScopes)
						.anyExchange().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.decoder(jwtDecoder))
						.authenticationEntryPoint(authenticationFailureHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationFailureHandler)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}

	@Bean
	ReactiveJwtDecoder jwtDecoder(GatewaySecurityProperties security) {
		return new SupplierReactiveJwtDecoder(() -> {
			String issuer = security.issuerUri().toString();
			NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuer)
					.jwsAlgorithm(SignatureAlgorithm.RS256)
					.build();
			OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
					JwtValidators.createDefaultWithIssuer(issuer),
					new JwtAudienceValidator(security.audience()));
			decoder.setJwtValidator(validator);
			return decoder;
		});
	}
}