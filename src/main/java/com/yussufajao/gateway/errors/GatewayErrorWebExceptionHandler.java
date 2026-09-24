package com.yussufajao.gateway.errors;

import com.yussufajao.gateway.correlation.GatewayAttributes;
import com.yussufajao.gateway.correlation.CorrelationId;
import com.yussufajao.gateway.routing.GatewayHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

	private final GatewayProblemMapper mapper;

	public GatewayErrorWebExceptionHandler(ErrorAttributes errorAttributes, WebProperties webProperties,
			ApplicationContext applicationContext, ServerCodecConfigurer serverCodecConfigurer,
			GatewayProblemMapper mapper) {
		super(errorAttributes, webProperties.getResources(), applicationContext);
		this.mapper = mapper;
		setMessageReaders(serverCodecConfigurer.getReaders());
		setMessageWriters(serverCodecConfigurer.getWriters());
	}

	@Override
	protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
		return RouterFunctions.route(RequestPredicates.all(), this::render);
	}

	private Mono<ServerResponse> render(ServerRequest request) {
		String path = request.path();
		String correlationId = correlationId(request);
		GatewayProblem problem = mapper.toProblem(getError(request), path, correlationId);
		request.exchange().getAttributes().put(GatewayAttributes.UPSTREAM_OUTCOME, outcome(problem));
		return ServerResponse.status(problem.status())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.header(GatewayHeaders.CORRELATION_ID, correlationId)
				.bodyValue(problem);
	}

	@Override
	protected void logError(ServerRequest request, ServerResponse response, Throwable throwable) {
		String correlationId = correlationId(request);
		log.warn("gateway.error status={} path={} correlationId={} exceptionType={}",
				response.statusCode() != null ? response.statusCode().value() : 0,
				request.path(),
				correlationId,
				throwable.getClass().getName());
		log.debug("gateway.error.stack", throwable);
	}

	private static String correlationId(ServerRequest request) {
		Object attribute = request.exchange().getAttributes().get(GatewayAttributes.CORRELATION_ID);
		if (attribute instanceof String value && !value.isBlank()) {
			return value;
		}
		return CorrelationId.generate();
	}

	private static String outcome(GatewayProblem problem) {
		if (problem.status() >= 500) {
			return "error";
		}
		return "rejected";
	}
}
