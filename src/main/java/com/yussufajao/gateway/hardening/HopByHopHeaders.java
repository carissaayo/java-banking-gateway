package com.yussufajao.gateway.hardening;

import java.util.Set;

public final class HopByHopHeaders {

	public static final Set<String> NAMES = Set.of(
			"connection",
			"keep-alive",
			"proxy-authenticate",
			"proxy-authorization",
			"te",
			"trailer",
			"transfer-encoding",
			"upgrade",
			"x-forwarded-for",
			"x-forwarded-host",
			"x-forwarded-proto",
			"x-forwarded-port",
			"forwarded");

	private HopByHopHeaders() {
	}
}