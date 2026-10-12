package com.yussufajao.gateway.hardening;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class InboundHeaderSanitizingWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest().mutate()
				.headers(this::strip)
				.build();
		return chain.filter(exchange.mutate().request(request).build());
	}

	private void strip(HttpHeaders headers) {
		for (String extra : headers.getOrEmpty(HttpHeaders.CONNECTION)) {
			for (String token : extra.split(",")) {
				String name = token.trim();
				if (!name.isEmpty()) {
					headers.remove(name);
				}
			}
		}
		for (String name : HopByHopHeaders.NAMES) {
			headers.remove(name);
		}
	}
}