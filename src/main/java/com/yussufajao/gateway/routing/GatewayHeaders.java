package com.yussufajao.gateway.routing;

public final class GatewayHeaders {

	public static final String ROUTE_ID = "X-Gateway-Route";
	public static final String CORRELATION_ID = "X-Correlation-ID";
	public static final String TRACEPARENT = "traceparent";
	public static final String TRACESTATE = "tracestate";

	private GatewayHeaders() {
	}
}
