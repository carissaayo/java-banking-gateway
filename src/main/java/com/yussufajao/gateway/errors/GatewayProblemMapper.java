package com.yussufajao.gateway.errors;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;

@Component
public class GatewayProblemMapper {

	public GatewayProblem toProblem(Throwable error, String instance, String correlationId) {
		Throwable cause = unwrap(error);
		GatewayErrorCode code = classify(cause);
		int status = statusOf(cause, code);
		return GatewayProblem.of(code, status, instance, correlationId);
	}

	public GatewayProblem upstreamError(int status, String instance, String correlationId) {
		return GatewayProblem.of(GatewayErrorCode.UPSTREAM_ERROR, status, instance, correlationId);
	}

	private static GatewayErrorCode classify(Throwable error) {
		if (isNotFound(error)) {
			return GatewayErrorCode.ROUTE_NOT_FOUND;
		}
		if (isTimeout(error)) {
			return GatewayErrorCode.UPSTREAM_TIMEOUT;
		}
		if (isUnavailable(error)) {
			return GatewayErrorCode.UPSTREAM_UNAVAILABLE;
		}
		return GatewayErrorCode.INTERNAL_ERROR;
	}

	private static int statusOf(Throwable error, GatewayErrorCode code) {
		if (error instanceof ResponseStatusException responseStatus) {
			HttpStatusCode status = responseStatus.getStatusCode();
			if (status.is4xxClientError() || status.is5xxServerError()) {
				return status.value();
			}
		}
		return code.defaultStatus();
	}

	private static boolean isNotFound(Throwable error) {
		if (error instanceof NoResourceFoundException) {
			return true;
		}
		return error instanceof ResponseStatusException responseStatus
				&& responseStatus.getStatusCode() == HttpStatus.NOT_FOUND;
	}

	private static boolean isTimeout(Throwable error) {
		if (error instanceof TimeoutException) {
			return true;
		}
		return hasCause(error, TimeoutException.class);
	}

	private static boolean isUnavailable(Throwable error) {
		return hasCause(error, ConnectException.class) || hasCause(error, UnknownHostException.class);
	}

	private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		Throwable current = error;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static Throwable unwrap(Throwable error) {
		if (error == null) {
			return new IllegalStateException("unknown");
		}
		return Exceptions.unwrap(error);
	}
}
