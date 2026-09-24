package com.yussufajao.gateway.errors;

public record GatewayProblem(
		String type,
		String title,
		int status,
		String detail,
		String instance,
		String correlationId
) {

	public static GatewayProblem of(GatewayErrorCode code, int status, String instance, String correlationId) {
		return new GatewayProblem(code.type(), code.title(), status, code.detail(), instance, correlationId);
	}
}
