package com.yussufajao.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedClientTest {

	@Test
	void prefersAzpThenClientId() {
		Jwt jwt = jwtBuilder()
				.claim("azp", "customer-app")
				.claim("client_id", "ignored")
				.subject("user-1")
				.build();

		JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);

		assertThat(AuthenticatedClient.clientId(authentication)).isEqualTo("customer-app");
		assertThat(AuthenticatedClient.subject(authentication)).isEqualTo("user-1");
	}

	@Test
	void usesClientIdWhenAzpMissing() {
		Jwt jwt = jwtBuilder().claim("client_id", "service-app").subject("svc-1").build();

		assertThat(AuthenticatedClient.clientId(new JwtAuthenticationToken(jwt))).isEqualTo("service-app");
	}

	@Test
	void anonymousHasNoIdentity() {
		AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
				"key",
				"anonymous",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

		assertThat(AuthenticatedClient.clientId(anonymous)).isEmpty();
		assertThat(AuthenticatedClient.subject(anonymous)).isEmpty();
	}

	private static Jwt.Builder jwtBuilder() {
		return Jwt.withTokenValue("token")
				.header("alg", "RS256")
				.audience(List.of("banking-gateway"))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60));
	}
}
