package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.yussufajao.gateway.security.TestJwtConfiguration;
import com.yussufajao.gateway.security.TestJwtTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewayPrometheusIT {

	@RegisterExtension
	static final WireMockExtension ledger = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void registerUpstreams(DynamicPropertyRegistry registry) {
		registry.add("gateway.upstreams.ledger", () -> "http://127.0.0.1:" + ledger.getPort());
		registry.add("gateway.upstreams.customer", () -> "http://127.0.0.1:65534");
	}

	@Autowired
	WebTestClient webTestClient;

	@Test
	void prometheusRequiresAToken() {
		webTestClient.get()
				.uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void scrapeContainsJvmGatewayAndPolicySeriesWithoutAccountIds() {
		ledger.stubFor(get(urlEqualTo("/accounts/acc-1"))
				.willReturn(okJson("{\"id\":\"acc-1\"}")));

		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.exchange()
				.expectStatus().isUnauthorized();

		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isOk();

		webTestClient.get()
				.uri("/actuator/env")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isNotFound();

		String body = webTestClient.get()
				.uri("/actuator/prometheus")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueMatches(HttpHeaders.CONTENT_TYPE, "text/plain.*")
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();

		assertThat(body).contains("jvm_memory_used_bytes");
		assertThat(body).contains("gateway_auth_failures_total");
		assertThat(body).contains("invalid-credentials");
		assertThat(body).contains("resilience4j_circuitbreaker");
		assertThat(body).doesNotContain("acc-1");
	}
}
