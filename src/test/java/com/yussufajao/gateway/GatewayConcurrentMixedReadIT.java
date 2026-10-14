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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewayConcurrentMixedReadIT {

    private static final int PER_ROUTE = 16;
    private static final int CONCURRENCY = 8;
    private static final int UPSTREAM_DELAY_MS = 50;

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

    WebClient client;

    @BeforeEach
    void connect() {
        client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        ledger.stubFor(get(urlEqualTo("/accounts/acc-mix"))
                .willReturn(jsonDelayed("{\"id\":\"acc-mix\"}")));
        customer.stubFor(get(urlEqualTo("/customers/c-mix"))
                .willReturn(jsonDelayed("{\"id\":\"c-mix\"}")));
    }

	@Test
	void ledgerAndCustomerReadsOverlapAndBothStay200() {
		String auth = TestJwtTokens.bearer("ledger.accounts.read", "customers.read");
		getStatus("/api/ledger/accounts/acc-mix", auth).block();
		getStatus("/api/customers/c-mix", auth).block();
		ledger.resetRequests();
		customer.resetRequests();

		long started = System.nanoTime();

		List<Integer> statuses = Flux.merge(
						repeatGet("/api/ledger/accounts/acc-mix", auth),
						repeatGet("/api/customers/c-mix", auth))
				.collectList()
				.block();

		long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
		long sequentialMs = (long) PER_ROUTE * 2 * UPSTREAM_DELAY_MS;

		assertThat(statuses).hasSize(PER_ROUTE * 2).allMatch(status -> status == 200);
		assertThat(elapsedMs).isGreaterThanOrEqualTo(UPSTREAM_DELAY_MS);
		assertThat(elapsedMs).isLessThan(sequentialMs);
		ledger.verify(PER_ROUTE, getRequestedFor(urlEqualTo("/accounts/acc-mix")));
		customer.verify(PER_ROUTE, getRequestedFor(urlEqualTo("/customers/c-mix")));

        System.out.println("GatewayConcurrentMixedReadIT elapsedMs=" + elapsedMs
                + " sequentialFloorMs=" + sequentialMs);
    }

    private Flux<Integer> repeatGet(String path, String auth) {
        return Flux.range(0, PER_ROUTE)
                .flatMap(i -> getStatus(path, auth), CONCURRENCY);
    }

    private Mono<Integer> getStatus(String path, String auth) {
        return client.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().value()));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonDelayed(String body) {
        return aResponse()
                .withFixedDelay(UPSTREAM_DELAY_MS)
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(body);
    }
}