package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewayCircuitBreakerIT {

	@RegisterExtension
	static final WireMockExtension ledger = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@RegisterExtension
	static final WireMockExtension customer = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void registerUpstreams(DynamicPropertyRegistry registry) {
		registry.add("gateway.upstreams.ledger", () -> "http://127.0.0.1:" + ledger.getPort());
		registry.add("gateway.upstreams.customer", () -> "http://127.0.0.1:" + customer.getPort());
	}

	@Autowired
	WebTestClient webTestClient;

	@Test
	void ledgerFailuresOpenBreakerAndLeaveCustomerAlone() {
		ledger.stubFor(get(urlEqualTo("/accounts/acc-down"))
				.willReturn(aResponse().withStatus(500).withBody("{\"error\":\"down\"}")));
		customer.stubFor(get(urlEqualTo("/customers/c-1"))
				.willReturn(okJson("{\"id\":\"c-1\"}")));

		for (int i = 0; i < 4; i++) {
			webTestClient.get()
					.uri("/api/ledger/accounts/acc-down")
					.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
					.exchange()
					.expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		webTestClient.get()
				.uri("/api/ledger/accounts/acc-down")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo("https://errors.example.test/circuit-open")
				.jsonPath("$.status").isEqualTo(503);

		ledger.verify(4, getRequestedFor(urlEqualTo("/accounts/acc-down")));

		webTestClient.get()
				.uri("/api/customers/c-1")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("customers.read"))
				.exchange()
				.expectStatus().isOk();
	}
}