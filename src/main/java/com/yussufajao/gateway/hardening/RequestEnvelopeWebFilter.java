package com.yussufajao.gateway.hardening;

import com.yussufajao.gateway.errors.GatewayErrorCode;
import com.yussufajao.gateway.errors.GatewayProblemHttpWriter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(75)
public class RequestEnvelopeWebFilter implements WebFilter {

	private final GatewayProblemHttpWriter problemWriter;

	public RequestEnvelopeWebFilter(GatewayProblemHttpWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		HttpHeaders headers = exchange.getRequest().getHeaders();
		if (hasLengthAndTransferEncoding(headers)) {
			return problemWriter.write(
					exchange,
					GatewayErrorCode.INVALID_REQUEST_ENVELOPE,
					HttpStatus.BAD_REQUEST);
		}
		HttpMethod method = exchange.getRequest().getMethod();
		if (isWrite(method) && isApi(exchange) && !isJson(headers.getContentType())) {
			return problemWriter.write(
					exchange,
					GatewayErrorCode.UNSUPPORTED_MEDIA_TYPE,
					HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		}
		return chain.filter(exchange);
	}

	private static boolean hasLengthAndTransferEncoding(HttpHeaders headers) {
		return headers.getFirst(HttpHeaders.CONTENT_LENGTH) != null
				&& !headers.getOrEmpty(HttpHeaders.TRANSFER_ENCODING).isEmpty();
	}

	private static boolean isWrite(HttpMethod method) {
		return HttpMethod.POST.equals(method) || HttpMethod.PATCH.equals(method);
	}

	private static boolean isApi(ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getRawPath();
		return path != null && path.startsWith("/api/");
	}

	private static boolean isJson(MediaType contentType) {
		return contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
	}
}
