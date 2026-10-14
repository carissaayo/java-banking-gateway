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
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@DirtiesContext
class GatewayGracefulShutdownIT {

    private static final int UPSTREAM_DELAY_MS = 800;

    @RegisterExtension
    static final WireMockExtension customer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerUpstreams(DynamicPropertyRegistry registry) {
        registry.add("gateway.upstreams.ledger", () -> "http://127.0.0.1:65534");
        registry.add("gateway.upstreams.customer", () -> "http://127.0.0.1:" + customer.getPort());
    }

    @Autowired
    WebServerApplicationContext context;

    @LocalServerPort
    int port;

    WebClient client;

    @BeforeEach
    void connect() {
        client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        customer.stubFor(get(urlEqualTo("/customers/c-drain"))
                .willReturn(aResponse()
                        .withFixedDelay(UPSTREAM_DELAY_MS)
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"id\":\"c-drain\"}")));
    }

    @Test
    void inFlightReadCompletesThenNewRequestsAreRejected() throws InterruptedException {
        AtomicReference<Throwable> failed = new AtomicReference<>();

        String auth = TestJwtTokens.bearer("customers.read");
        Mono<Integer> inFlight = getStatus("/api/customers/c-drain", auth).cache();
        inFlight.subscribe(ignored -> {
        }, failed::set);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline
                && customer.findAll(getRequestedFor(urlEqualTo("/customers/c-drain"))).isEmpty()
                && failed.get() == null) {
            Thread.sleep(50);
        }
        assertThat(failed.get()).as("in-flight error").isNull();
        assertThat(customer.findAll(getRequestedFor(urlEqualTo("/customers/c-drain"))))
                .as("WireMock should see the request before shutdown")
                .isNotEmpty();

        CountDownLatch drained = new CountDownLatch(1);
        context.getWebServer().shutDownGracefully(result -> drained.countDown());

        Integer inFlightStatus = inFlight.block(Duration.ofSeconds(5));
        Integer afterShutdown = getStatus("/api/customers/c-drain", auth)
                .onErrorReturn(-1)
                .block(Duration.ofSeconds(2));

        assertThat(inFlightStatus).isIn(200, 504);
        assertThat(drained.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(afterShutdown).isNotEqualTo(200);

        System.out.println("GatewayGracefulShutdownIT inFlightStatus=" + inFlightStatus
                + " afterShutdown=" + afterShutdown);
    }

    private Mono<Integer> getStatus(String path, String auth) {
        return client.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().value()));
    }
}