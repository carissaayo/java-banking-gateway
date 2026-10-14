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
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RequestEnvelopeWebFilterTest {

	@Test
	void xmlWriteIs415AndDoesNotContinue() {
		GatewayProblemHttpWriter writer = mockWriter();
		var filter = new RequestEnvelopeWebFilter(writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/ledger/transfers")
				.contentType(MediaType.APPLICATION_XML)
				.body("<transfer/>"));
		WebFilterChain chain = mock(WebFilterChain.class);
		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(writer).write(
				eq(exchange),
				eq(GatewayErrorCode.UNSUPPORTED_MEDIA_TYPE),
				eq(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
		verify(chain, never()).filter(any());
	}

	@Test
	void contentLengthAndTransferEncodingIs400() {
		GatewayProblemHttpWriter writer = mockWriter();
		var filter = new RequestEnvelopeWebFilter(writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/ledger/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.CONTENT_LENGTH, "10")
				.header(HttpHeaders.TRANSFER_ENCODING, "chunked")
				.body("{}"));
		WebFilterChain chain = mock(WebFilterChain.class);
		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(writer).write(
				eq(exchange),
				eq(GatewayErrorCode.INVALID_REQUEST_ENVELOPE),
				eq(HttpStatus.BAD_REQUEST));
		verify(chain, never()).filter(any());
	}

	@Test
	void getWithoutContentTypeContinues() {
		GatewayProblemHttpWriter writer = mock(GatewayProblemHttpWriter.class);
		var filter = new RequestEnvelopeWebFilter(writer);
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ledger/accounts/acc-1"));
		WebFilterChain chain = mock(WebFilterChain.class);
		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(chain).filter(exchange);
		verify(writer, never()).write(any(), any(), any());
	}

	private static GatewayProblemHttpWriter mockWriter() {
		GatewayProblemHttpWriter writer = mock(GatewayProblemHttpWriter.class);
		when(writer.write(any(), any(), any())).thenReturn(Mono.empty());
		return writer;
	}
}
