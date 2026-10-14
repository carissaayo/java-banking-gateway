package com.yussufajao.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.yussufajao.gateway.routing.GatewayHeaders;
import com.yussufajao.gateway.security.TestJwtConfiguration;
import com.yussufajao.gateway.security.TestJwtTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwtConfiguration.class)
class GatewayConcurrentBurstIT {

    private static final int BURST = 12;
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
        registry.add("GATEWAY_REDIS_URI", GatewayConcurrentBurstIT::redisUri);
    }

    @LocalServerPort
    int port;

    WebClient client;

    @BeforeEach
    void connect() {
        client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        ledger.stubFor(post(urlEqualTo("/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{}")));
    }

    @Test
    void concurrentTransferBurstSameIdentityReturnsSome429() {
        String auth = TestJwtTokens.bearerFor(
                "customer-app",
                "burst-" + UUID.randomUUID(),
                "ledger.transfers.write");

        List<Integer> statuses = Flux.range(0, BURST)
                .flatMap(i -> client.post()
                        .uri("/api/ledger/transfers")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .header(GatewayHeaders.IDEMPOTENCY_KEY, "burst-" + i + "-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{}")
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())),
                        BURST)
                .collectList()
                .block();

        long ok = statuses.stream().filter(status -> status == 200).count();
        long limited = statuses.stream().filter(status -> status == 429).count();

        assertThat(statuses).hasSize(BURST);
        assertThat(statuses).allMatch(status -> status == 200 || status == 429);
        assertThat(ok).isBetween(1L, 6L);
        assertThat(limited).isGreaterThanOrEqualTo(1L);
        ledger.verify((int) ok, postRequestedFor(urlEqualTo("/transfers")));

        System.out.println("GatewayConcurrentBurstIT ok=" + ok + " limited=" + limited);
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
        } catch (RuntimeException ex) {
            return null;
        }
    }
}