package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.github.tomakehurst.wiremock.http.Fault;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewayRetryIT {

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
	void getAccountsRetriesAfterConnectionResetThenSucceeds() {
		ledger.stubFor(get(urlEqualTo("/accounts/acc-flaky"))
				.inScenario("get-retry")
				.whenScenarioStateIs(STARTED)
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
				.willSetStateTo("second"));
		ledger.stubFor(get(urlEqualTo("/accounts/acc-flaky"))
				.inScenario("get-retry")
				.whenScenarioStateIs("second")
				.willReturn(okJson("{\"id\":\"acc-flaky\"}")));

		webTestClient.get()
				.uri("/api/ledger/accounts/acc-flaky")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isOk();

		ledger.verify(2, getRequestedFor(urlEqualTo("/accounts/acc-flaky")));
	}

	@Test
	void postTransferIsNotRetried() {
		ledger.stubFor(post(urlEqualTo("/transfers"))
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		webTestClient.post()
				.uri("/api/ledger/transfers")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.transfers.write"))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"amount\":\"1.00\"}")
				.exchange()
				.expectStatus().is5xxServerError();

		ledger.verify(1, postRequestedFor(urlEqualTo("/transfers")));
	}
}