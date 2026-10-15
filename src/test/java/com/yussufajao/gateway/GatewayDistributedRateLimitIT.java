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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
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
class GatewayDistributedRateLimitIT {

	private static final int PER_INSTANCE = 8;
	private static final GenericContainer<?> REDIS = startRedisIfDockerIsAvailable();
	private static ConfigurableApplicationContext instanceB;

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
		registry.add("GATEWAY_REDIS_URI", GatewayDistributedRateLimitIT::redisUri);
	}

	@LocalServerPort
	int portA;

	@BeforeEach
	void startSecondInstance() {
		ledger.stubFor(post(urlEqualTo("/transfers"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.withBody("{}")));
		if (instanceB == null || !instanceB.isActive()) {
			instanceB = new SpringApplicationBuilder(BankingGatewayApplication.class, TestJwtConfiguration.class)
					.run(
							"--server.port=0",
							"--spring.main.web-application-type=reactive",
							"--gateway.upstreams.ledger=http://127.0.0.1:" + ledger.getPort(),
							"--gateway.upstreams.customer=http://127.0.0.1:65534",
							"--GATEWAY_SECURITY_ISSUER_URI=" + TestJwtTokens.ISSUER,
							"--GATEWAY_SECURITY_AUDIENCE=" + TestJwtTokens.AUDIENCE,
							"--GATEWAY_REDIS_URI=" + redisUri());
		}
	}

	@AfterAll
	static void stopSecondInstance() {
		if (instanceB != null) {
			instanceB.close();
			instanceB = null;
		}
	}

	@Test
	void twoGatewaysShareOneRedisBucket() {
		int portB = ((WebServerApplicationContext) instanceB).getWebServer().getPort();
		WebClient a = WebClient.builder().baseUrl("http://127.0.0.1:" + portA).build();
		WebClient b = WebClient.builder().baseUrl("http://127.0.0.1:" + portB).build();
		String auth = TestJwtTokens.bearerFor(
				"customer-app",
				"dist-" + UUID.randomUUID(),
				"ledger.transfers.write");

		List<Integer> statuses = Flux.merge(burst(a, auth, "a"), burst(b, auth, "b"))
				.collectList()
				.block();

		long ok = statuses.stream().filter(status -> status == 200).count();
		long limited = statuses.stream().filter(status -> status == 429).count();

		assertThat(statuses).hasSize(PER_INSTANCE * 2);
		assertThat(statuses).allMatch(status -> status == 200 || status == 429);
		assertThat(ok).as("shared Redis must not allow 2x burstCapacity").isLessThanOrEqualTo(8L);
		assertThat(limited).isGreaterThanOrEqualTo(4L);
		ledger.verify((int) ok, postRequestedFor(urlEqualTo("/transfers")));
	}

	private static Flux<Integer> burst(WebClient client, String auth, String prefix) {
		return Flux.range(0, PER_INSTANCE)
				.flatMap(i -> client.post()
						.uri("/api/ledger/transfers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.header(GatewayHeaders.IDEMPOTENCY_KEY, prefix + "-" + i + "-" + UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue("{}")
						.exchangeToMono(response -> response.releaseBody()
								.thenReturn(response.statusCode().value())),
						PER_INSTANCE);
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
