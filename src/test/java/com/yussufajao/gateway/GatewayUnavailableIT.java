package com.yussufajao.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.yussufajao.gateway.routing.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"gateway.upstreams.ledger=http://127.0.0.1:59999",
				"gateway.upstreams.customer=http://127.0.0.1:59998"
		})
class GatewayUnavailableIT {

	@Autowired
	WebTestClient webTestClient;

	@Test
	void connectionFailureDoesNotLeakUpstreamHost() {
		webTestClient.get()
				.uri("/api/ledger/accounts/acc-1")
				.header(GatewayHeaders.CORRELATION_ID, "corr-unavail")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectHeader().valueEquals(GatewayHeaders.CORRELATION_ID, "corr-unavail")
				.expectBody()
				.jsonPath("$.type").isEqualTo("https://errors.example.test/upstream-unavailable")
				.jsonPath("$.status").isEqualTo(503)
				.jsonPath("$.correlationId").isEqualTo("corr-unavail")
				.jsonPath("$.detail").value(detail -> assertThat((String) detail)
						.doesNotContain("127.0.0.1")
						.doesNotContain("59999")
						.doesNotContain("Exception")
						.doesNotContain("Connection"))
				.jsonPath("$.trace").doesNotExist()
				.jsonPath("$.exception").doesNotExist();
	}
}
