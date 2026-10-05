package com.yussufajao.gateway.security;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

	private final GatewayAuthenticationFailureHandler problemWriter;

	public GatewayAccessDeniedHandler(GatewayAuthenticationFailureHandler problemWriter) {
		this.problemWriter = problemWriter;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
		return problemWriter.write(exchange, GatewayErrorCode.INSUFFICIENT_SCOPE, HttpStatus.FORBIDDEN);
	}
}