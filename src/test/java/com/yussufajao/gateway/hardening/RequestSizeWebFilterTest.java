package com.yussufajao.gateway.hardening;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RequestSizeWebFilterTest {

	@Test
	void hugeContentLengthWrites413AndDoesNotContinue() {
		GatewayProblemHttpWriter writer = mock(GatewayProblemHttpWriter.class);
		when(writer.write(any(), any(), any())).thenReturn(Mono.empty());
		var filter = new RequestSizeWebFilter(writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/ledger/transfers")
				.header(HttpHeaders.CONTENT_LENGTH, "100000"));
		WebFilterChain chain = mock(WebFilterChain.class);
		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(writer).write(
				eq(exchange),
				eq(GatewayErrorCode.REQUEST_TOO_LARGE),
				eq(HttpStatus.PAYLOAD_TOO_LARGE));
		verify(chain, never()).filter(any());
	}
}
