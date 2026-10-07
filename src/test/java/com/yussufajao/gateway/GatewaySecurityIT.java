package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.yussufajao.gateway.routing.GatewayHeaders;
import com.yussufajao.gateway.security.TestJwtConfiguration;
import com.yussufajao.gateway.security.TestJwtTokens;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewaySecurityIT {

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

	@BeforeEach
	void stubUpstreams() {
		ledger.stubFor(get(urlEqualTo("/accounts/acc-1"))
				.willReturn(json(200, "{\"id\":\"acc-1\"}")));
		ledger.stubFor(post(urlEqualTo("/transfers"))
				.willReturn(json(201, "{\"id\":\"txn-1\"}")));
		customer.stubFor(post(urlEqualTo("/operations/status"))
				.willReturn(json(200, "{\"status\":\"ok\"}")));
		customer.stubFor(get(urlEqualTo("/operations/status"))
				.willReturn(json(200, "{\"status\":\"ok\"}")));
	}

	@Test
	void missingAuthorizationIsInvalidCredentials() {
		expectProblem(webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().exists(GatewayHeaders.CORRELATION_ID)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody(),
				401, "invalid-credentials");
		ledger.verify(0, getRequestedFor(urlEqualTo("/accounts/acc-1")));
	}

	@Test
	void accessTokenQueryParameterIsRejected() {
		expectProblem(webTestClient.get()
				.uri("/api/ledger/accounts/acc-1?access_token=leak")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectBody(),
				401, "invalid-credentials")
				.jsonPath("$.detail").value(detail -> assertThat((String) detail).doesNotContain("leak"));
		ledger.verify(0, getRequestedFor(urlEqualTo("/accounts/acc-1")));
	}

	@Test
	void headerPlusQueryTokenIsRejected() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1?access_token=leak")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isUnauthorized();
		ledger.verify(0, getRequestedFor(urlEqualTo("/accounts/acc-1")));
	}

	@Test
	void expiredTokenIsRejected() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.expired())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void wrongIssuerIsRejected() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.wrongIssuer())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void wrongAudienceIsRejected() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.wrongAudience())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void hmacAndUnsignedTokensAreRejected() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.hs256())
				.exchange()
				.expectStatus().isUnauthorized();
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.algNone())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void validCustomerReadReachesLedger() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo("acc-1");
		ledger.verify(getRequestedFor(urlEqualTo("/accounts/acc-1")));
	}

	@Test
	void missingTransferWriteScopeIsForbidden() {
		expectProblem(webTestClient.post()
				.uri("/api/ledger/transfers")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isForbidden()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody(),
				403, "insufficient-scope");
		ledger.verify(0, postRequestedFor(urlEqualTo("/transfers")));
	}

	@Test
	void operationsAdminCanPostStatus() {
		webTestClient.post()
				.uri("/api/operations/status")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("operations.admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk();
		customer.verify(postRequestedFor(urlEqualTo("/operations/status")));
	}

	@Test
	void customerTokenCannotCallOperations() {
		webTestClient.get()
				.uri("/api/operations/status")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isForbidden();
		customer.verify(0, getRequestedFor(urlEqualTo("/operations/status")));
	}

	@Test
	void validTokenOnUnknownPathIsNotFound() {
		expectProblem(webTestClient.get()
				.uri("/api/does-not-exist")
				.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
				.exchange()
				.expectStatus().isNotFound()
				.expectBody(),
				404, "route-not-found");
	}

	@Test
	void healthDoesNotRequireAToken() {
		webTestClient.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	private static BodyContentSpec expectProblem(BodyContentSpec body, int status, String slug) {
		return body.jsonPath("$.type").isEqualTo("https://errors.example.test/" + slug)
				.jsonPath("$.status").isEqualTo(status)
				.jsonPath("$.correlationId").exists()
				.jsonPath("$.exception").doesNotExist()
				.jsonPath("$.trace").doesNotExist()
				.jsonPath("$.detail").value(detail -> assertThat((String) detail)
						.doesNotContain("Exception")
						.doesNotContain("Bearer")
						.doesNotContain("eyJ"));
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body);
	}
}
