package com.yussufajao.gateway.hardening;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class InboundHeaderSanitizingWebFilterTest {

	@Test
	void stripsForwardedAndConnectionHeaders() {
		var filter = new InboundHeaderSanitizingWebFilter();
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ledger/accounts/acc-1")
				.header("X-Forwarded-For", "8.8.8.8")
				.header(HttpHeaders.CONNECTION, "close")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test"));
		filter.filter(exchange, ex -> {
			assertThat(ex.getRequest().getHeaders().getFirst("X-Forwarded-For")).isNull();
			assertThat(ex.getRequest().getHeaders().getFirst(HttpHeaders.CONNECTION)).isNull();
			assertThat(ex.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test");
			return Mono.empty();
		}).block();
	}
}
