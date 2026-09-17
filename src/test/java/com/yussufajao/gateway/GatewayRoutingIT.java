package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import com.yussufajao.gateway.routing.StaticRouteCatalogConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {

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

	@LocalServerPort
	int port;

	@Autowired
	WebTestClient webTestClient;

	@BeforeEach
	void stubUpstreams() {
		ledger.stubFor(get(urlEqualTo("/accounts/acc-1"))
				.willReturn(json(200, "{\"id\":\"acc-1\"}")));
		ledger.stubFor(post(urlEqualTo("/transfers"))
				.willReturn(json(201, "{\"id\":\"txn-1\"}")));
		ledger.stubFor(get(urlEqualTo("/transactions/txn-1"))
				.willReturn(json(200, "{\"id\":\"txn-1\"}")));
		customer.stubFor(get(urlEqualTo("/customers/cus-1"))
				.willReturn(json(200, "{\"id\":\"cus-1\"}")));
		customer.stubFor(get(urlEqualTo("/operations/status"))
				.willReturn(json(200, "{\"status\":\"ok\"}")));
	}

	@Test
	void ledgerReadIsRewrittenAndRouted() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(GatewayHeaders.ROUTE_ID, StaticRouteCatalogConfiguration.LEDGER_WRITE)
				.expectBody()
				.jsonPath("$.id").isEqualTo("acc-1");

		ledger.verify(getRequestedFor(urlEqualTo("/accounts/acc-1")));
	}

	@Test
	void transferWriteForwardsIdempotencyKey() {
		webTestClient.post()
				.uri("/api/ledger/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Idempotency-Key", "idem-123")
				.bodyValue("{\"amountMinor\":100}")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.CREATED)
				.expectHeader().valueEquals(GatewayHeaders.ROUTE_ID, StaticRouteCatalogConfiguration.LEDGER_WRITE)
				.expectBody()
				.jsonPath("$.id").isEqualTo("txn-1");

		ledger.verify(postRequestedFor(urlEqualTo("/transfers"))
				.withHeader("Idempotency-Key", equalTo("idem-123")));
	}

	@Test
	void transactionQueryIsRewrittenAndRouted() {
		webTestClient.get()
				.uri("/api/transactions/txn-1")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(GatewayHeaders.ROUTE_ID, StaticRouteCatalogConfiguration.TRANSACTION_QUERY)
				.expectBody()
				.jsonPath("$.id").isEqualTo("txn-1");

		ledger.verify(getRequestedFor(urlEqualTo("/transactions/txn-1")));
	}

	@Test
	void customerReadIsRewrittenAndRouted() {
		webTestClient.get()
				.uri("/api/customers/cus-1")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(GatewayHeaders.ROUTE_ID, StaticRouteCatalogConfiguration.CUSTOMER)
				.expectBody()
				.jsonPath("$.id").isEqualTo("cus-1");

		customer.verify(getRequestedFor(urlEqualTo("/customers/cus-1")));
	}

	@Test
	void operationsReadIsRewrittenAndRouted() {
		webTestClient.get()
				.uri("/api/operations/status")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(GatewayHeaders.ROUTE_ID, StaticRouteCatalogConfiguration.OPERATIONS)
				.expectBody()
				.jsonPath("$.status").isEqualTo("ok");

		customer.verify(getRequestedFor(urlEqualTo("/operations/status")));
	}

	@Test
	void unknownRouteFailsWithNotFound() {
		webTestClient.get()
				.uri("/api/unknown/resource")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void disallowedMethodFailsWithNotFound() {
		webTestClient.delete()
				.uri("/api/ledger/accounts/acc-1")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void healthEndpointsAreAvailable() {
		assertThat(port).isPositive();

		webTestClient.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");

		webTestClient.get()
				.uri("/actuator/health/liveness")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");

		webTestClient.get()
				.uri("/actuator/health/readiness")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body);
	}
}
