package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.yussufajao.gateway.routing.GatewayHeaders;
import com.yussufajao.gateway.security.TestJwtConfiguration;
import com.yussufajao.gateway.security.TestJwtTokens;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestJwtConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRateLimitIT {

	private static final String TRANSFER_SCOPE = "ledger.transfers.write";
	private static final String TXN_SCOPE = "transactions.read";

	private static final GenericContainer<?> REDIS = startRedisIfDockerIsAvailable();

	@RegisterExtension
	static final WireMockExtension ledger = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("gateway.upstreams.ledger", () -> "http://127.0.0.1:" + ledger.getPort());
		registry.add("gateway.upstreams.customer", () -> "http://127.0.0.1:65534");
		registry.add("GATEWAY_SECURITY_ISSUER_URI", () -> TestJwtTokens.ISSUER);
		registry.add("GATEWAY_SECURITY_AUDIENCE", () -> TestJwtTokens.AUDIENCE);
		registry.add("GATEWAY_REDIS_URI", GatewayRateLimitIT::redisUri);
	}

	@Autowired
	WebTestClient webTestClient;

	@BeforeEach
	void stubLedger() {
		ledger.stubFor(post(urlEqualTo("/transfers"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{}")));
		ledger.stubFor(get(urlEqualTo("/transactions/txn-1"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{}")));
	}

	@Test
	@Order(1)
	void transferWriteEventuallyReturns429() {
		String auth = TestJwtTokens.bearerFor("customer-app", "burst-" + UUID.randomUUID(), TRANSFER_SCOPE);
		boolean saw429 = false;
		for (int i = 0; i < 12; i++) {
			var result = postTransfer(auth).returnResult(byte[].class);
			if (result.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
				assertThat(result.getResponseHeaders().getFirst(GatewayHeaders.RATE_LIMIT_LIMIT)).isEqualTo("4");
				assertThat(new String(result.getResponseBodyContent())).contains("Rate limit exceeded");
				saw429 = true;
				break;
			}
			assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
		}
		assertThat(saw429).isTrue();
	}

	@Test
	@Order(2)
	void otherIdentityIsNotBlocked() {
		String alice = TestJwtTokens.bearerFor("customer-app", "alice-" + UUID.randomUUID(), TRANSFER_SCOPE);
		String bob = TestJwtTokens.bearerFor("customer-app", "bob-" + UUID.randomUUID(), TRANSFER_SCOPE);
		for (int i = 0; i < 4; i++) {
			postTransfer(alice).expectStatus().isOk();
		}
		postTransfer(alice).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		postTransfer(bob).expectStatus().isOk();
	}

	@Test
	@Order(3)
	void redisDownFailClosedOnTransfersFailOpenOnReads() {
		Assumptions.assumeTrue(REDIS != null && REDIS.isRunning(), "Docker Redis is required to stop the limiter");
		String write = TestJwtTokens.bearerFor("customer-app", "user-down-write", TRANSFER_SCOPE);
		String read = TestJwtTokens.bearerFor("service-app", "user-down-read", TXN_SCOPE);
		REDIS.stop();
		postTransfer(write).expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		webTestClient.get()
				.uri("/api/transactions/txn-1")
				.header(HttpHeaders.AUTHORIZATION, read)
				.exchange()
				.expectStatus().isOk();
	}

	private WebTestClient.ResponseSpec postTransfer(String authorization) {
		return webTestClient.post()
				.uri("/api/ledger/transfers")
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.header("Idempotency-Key", "it-1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange();
	}

	private static String redisUri() {
		if (REDIS != null && REDIS.isRunning()) {
			return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
		}
		return "redis://127.0.0.1:6379";
	}

	private static GenericContainer<?> startRedisIfDockerIsAvailable() {
		try {
			GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
					.withExposedPorts(6379);
			redis.start();
			return redis;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}
}
