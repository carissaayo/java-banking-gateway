package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.yussufajao.gateway.routing.GatewayHeaders;
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
class GatewayTimeoutIT {

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
	void slowLedgerTransferIs504WithoutLeakingHost() {
		ledger.stubFor(get(urlEqualTo("/accounts/acc-slow"))
				.willReturn(aResponse()
						.withFixedDelay(5000)
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"id\":\"acc-slow\"}")));

		webTestClient.get()
				.uri("/api/ledger/accounts/acc-slow")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo("https://errors.example.test/upstream-timeout")
				.jsonPath("$.status").isEqualTo(504)
				.jsonPath("$.detail").value(detail -> assertThat((String) detail)
						.doesNotContain("127.0.0.1")
						.doesNotContain("Exception"));
	}
}