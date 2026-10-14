package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.yussufajao.gateway.security.TestJwtConfiguration;
import com.yussufajao.gateway.security.TestJwtTokens;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewayConcurrentReadIT {

	private static final int REQUESTS = 32;
	private static final int CONCURRENCY = 8;
	private static final int UPSTREAM_DELAY_MS = 50;

	@RegisterExtension
	static final WireMockExtension ledger = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void registerUpstreams(DynamicPropertyRegistry registry) {
		registry.add("gateway.upstreams.ledger", () -> "http://127.0.0.1:" + ledger.getPort());
		registry.add("gateway.upstreams.customer", () -> "http://127.0.0.1:65534");
	}

	@LocalServerPort
	int port;

	WebClient client;

	@BeforeEach
	void connect() {
		client = WebClient.builder()
				.baseUrl("http://127.0.0.1:" + port)
				.build();
		ledger.stubFor(get(urlEqualTo("/accounts/acc-load"))
				.willReturn(aResponse()
						.withFixedDelay(UPSTREAM_DELAY_MS)
						.withStatus(200)
						.withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.withBody("{\"id\":\"acc-load\"}")));
	}

	@Test
	void overlappingAccountReadsFinishFasterThanSequential() {
		String auth = TestJwtTokens.bearer("ledger.accounts.read");
		client.get()
				.uri("/api/ledger/accounts/acc-load")
				.header(HttpHeaders.AUTHORIZATION, auth)
				.exchangeToMono(response -> response.releaseBody()
						.thenReturn(response.statusCode().value()))
				.block();
		ledger.resetRequests();

		long started = System.nanoTime();

		List<Integer> statuses = Flux.range(0, REQUESTS)
				.flatMap(
						i -> client.get()
								.uri("/api/ledger/accounts/acc-load")
								.header(HttpHeaders.AUTHORIZATION, auth)
								.exchangeToMono(response -> response.releaseBody()
										.thenReturn(response.statusCode().value())),
						CONCURRENCY)
				.collectList()
				.block();

		long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
		long sequentialMs = (long) REQUESTS * UPSTREAM_DELAY_MS;

		assertThat(statuses).hasSize(REQUESTS).allMatch(status -> status == 200);
		assertThat(elapsedMs).isGreaterThanOrEqualTo(UPSTREAM_DELAY_MS);
		assertThat(elapsedMs).isLessThan(sequentialMs);
		ledger.verify(REQUESTS, getRequestedFor(urlEqualTo("/accounts/acc-load")));

		System.out.println("GatewayConcurrentReadIT elapsedMs=" + elapsedMs
				+ " sequentialFloorMs=" + sequentialMs);
	}
}