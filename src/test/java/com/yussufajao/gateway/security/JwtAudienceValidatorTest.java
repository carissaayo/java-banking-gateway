package com.yussufajao.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAudienceValidatorTest {

	private final JwtAudienceValidator validator = new JwtAudienceValidator("banking-gateway");

	@Test
	void matchingAudiencePasses() {
		assertThat(validator.validate(jwt(List.of("banking-gateway"))).hasErrors()).isFalse();
	}

	@Test
	void multiAudienceContainingOursPasses() {
		assertThat(validator.validate(jwt(List.of("other-api", "banking-gateway"))).hasErrors()).isFalse();
	}

	@Test
	void wrongAudienceFails() {
		assertThat(validator.validate(jwt(List.of("other-api"))).hasErrors()).isTrue();
	}

	private static Jwt jwt(List<String> audience) {
		return Jwt.withTokenValue("token")
				.header("alg", "RS256")
				.audience(audience)
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60))
				.build();
	}
}
