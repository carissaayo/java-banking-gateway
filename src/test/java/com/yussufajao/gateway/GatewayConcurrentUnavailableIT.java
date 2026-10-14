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
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class GatewayConcurrentUnavailableIT {

    private static final int PER_ROUTE = 8;

    @RegisterExtension
    static final WireMockExtension customer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerUpstreams(DynamicPropertyRegistry registry) {
        registry.add("gateway.upstreams.ledger", () -> "http://127.0.0.1:65534");
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
        customer.stubFor(get(urlEqualTo("/customers/c-ok"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"id\":\"c-ok\"}")));
    }

    @Test
    void refusedLedgerDoesNotBlockCustomerReads() {
        String auth = TestJwtTokens.bearer("ledger.accounts.read", "customers.read");
        getStatus("/api/customers/c-ok", auth).block();
        customer.resetRequests();

        List<Integer> statuses = Flux.merge(
                Flux.range(0, PER_ROUTE).flatMap(i -> getStatus("/api/ledger/accounts/acc-down", auth), PER_ROUTE),
                Flux.range(0, PER_ROUTE).flatMap(i -> getStatus("/api/customers/c-ok", auth), PER_ROUTE))
                .collectList()
                .block();

        assertThat(statuses).hasSize(PER_ROUTE * 2);
        long customer200 = statuses.stream().filter(status -> status == 200).count();
        long ledgerFailed = statuses.stream().filter(status -> status != 200).count();
        assertThat(customer200).isEqualTo(PER_ROUTE);
        assertThat(ledgerFailed).isEqualTo(PER_ROUTE);
        assertThat(statuses).allMatch(status -> status == 200 || status == 503 || status == 500);
        customer.verify(PER_ROUTE, getRequestedFor(urlEqualTo("/customers/c-ok")));

        System.out.println("GatewayConcurrentUnavailableIT statuses=" + statuses
                + " customer200=" + customer200 + " ledgerFailed=" + ledgerFailed);
    }

    private Mono<Integer> getStatus(String path, String auth) {
        return client.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().value()));
    }
}