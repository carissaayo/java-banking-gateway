package com.yussufajao.gateway.errors;

public enum GatewayErrorCode {

	ROUTE_NOT_FOUND("route-not-found", "Route not found", 404,
			"No route matched this request."),
	UPSTREAM_UNAVAILABLE("upstream-unavailable", "Upstream unavailable", 503,
			"The upstream service is currently unavailable."),
	UPSTREAM_TIMEOUT("upstream-timeout", "Upstream timeout", 504,
			"The upstream service did not respond in time."),
	UPSTREAM_ERROR("upstream-error", "Upstream error", 500,
			"The upstream service failed to handle the request."),
	INTERNAL_ERROR("internal-error", "Internal error", 500,
			"The gateway could not complete the request.");

	private static final String TYPE_BASE = "https://errors.example.test/";

	private final String slug;
	private final String title;
	private final int defaultStatus;
	private final String detail;

	GatewayErrorCode(String slug, String title, int defaultStatus, String detail) {
		this.slug = slug;
		this.title = title;
		this.defaultStatus = defaultStatus;
		this.detail = detail;
	}

	public String type() {
		return TYPE_BASE + slug;
	}

	public String title() {
		return title;
	}

	public int defaultStatus() {
		return defaultStatus;
	}

	public String detail() {
		return detail;
	}
}
