package com.yussufajao.gateway.correlation;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

	private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,128}$");

	private CorrelationId() {
	}

	public static String resolve(List<String> headerValues) {
		if (headerValues == null || headerValues.size() != 1) {
			return generate();
		}
		String candidate = headerValues.getFirst();
		if (candidate != null && VALID.matcher(candidate).matches()) {
			return candidate;
		}
		return generate();
	}

	public static String generate() {
		return UUID.randomUUID().toString();
	}
}
