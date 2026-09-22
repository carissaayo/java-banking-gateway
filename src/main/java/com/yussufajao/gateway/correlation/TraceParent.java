package com.yussufajao.gateway.correlation;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TraceParent(String value, String traceId, String spanId) {

	private static final Pattern VALID = Pattern.compile(
			"^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
	private static final String ZERO_TRACE = "0".repeat(32);
	private static final String ZERO_SPAN = "0".repeat(16);
	private static final int TRACESTATE_MAX_LENGTH = 512;

	public static TraceParent childOf(List<String> headerValues) {
		if (headerValues != null && headerValues.size() == 1) {
			Matcher matcher = VALID.matcher(headerValues.getFirst());
			if (matcher.matches()) {
				String traceId = matcher.group(2);
				String parentSpan = matcher.group(3);
				if (!ZERO_TRACE.equals(traceId) && !ZERO_SPAN.equals(parentSpan)) {
					String spanId = randomHex(8);
					return new TraceParent("00-" + traceId + "-" + spanId + "-01", traceId, spanId);
				}
			}
		}
		return generate();
	}

	public static String sanitizeState(List<String> headerValues) {
		if (headerValues == null || headerValues.size() != 1) {
			return null;
		}
		String value = headerValues.getFirst();
		if (value == null || value.isBlank() || value.length() > TRACESTATE_MAX_LENGTH) {
			return null;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < 0x20 || c > 0x7E) {
				return null;
			}
		}
		return value;
	}

	public static TraceParent generate() {
		String traceId = randomHex(16);
		String spanId = randomHex(8);
		return new TraceParent("00-" + traceId + "-" + spanId + "-01", traceId, spanId);
	}

	private static String randomHex(int byteCount) {
		byte[] bytes = new byte[byteCount];
		ThreadLocalRandom.current().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
