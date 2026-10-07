package com.yussufajao.gateway.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@TestConfiguration
public class TestJwtConfiguration {

	@Bean
	@Primary
	ReactiveJwtDecoder testJwtDecoder() throws Exception {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
				.withPublicKey(TestJwtTokens.RSA_KEY.toRSAPublicKey())
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(TestJwtTokens.ISSUER),
				new JwtAudienceValidator(TestJwtTokens.AUDIENCE)));
		return decoder;
	}
}
