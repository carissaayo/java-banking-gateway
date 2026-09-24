package com.yussufajao.gateway.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GatewayProblemMapperTest {

	private final GatewayProblemMapper mapper = new GatewayProblemMapper();

	@Test
	void mapsMissingRouteWithoutExceptionMessage() {
		GatewayProblem problem = mapper.toProblem(
				new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching handler"),
				"/api/unknown",
				"corr-1234");

		assertThat(problem.status()).isEqualTo(404);
		assertThat(problem.type()).isEqualTo("https://errors.example.test/route-not-found");
		assertThat(problem.correlationId()).isEqualTo("corr-1234");
		assertThat(problem.detail()).doesNotContain("handler");
		assertThat(problem.detail()).doesNotContain("Exception");
	}

	@Test
	void mapsConnectionFailureWithoutHostnames() {
		GatewayProblem problem = mapper.toProblem(
				new ConnectException("Connection refused: localhost/127.0.0.1:8081"),
				"/api/ledger/accounts/acc-1",
				"corr-1234");

		assertThat(problem.status()).isEqualTo(503);
		assertThat(problem.type()).isEqualTo("https://errors.example.test/upstream-unavailable");
		assertThat(problem.detail()).doesNotContain("127.0.0.1");
		assertThat(problem.detail()).doesNotContain("localhost");
		assertThat(problem.detail()).doesNotContain("Connection refused");
	}
}
