package com.yussufajao.gateway.audit;

import java.net.URI;

public final class SafeRequestPath {

	private static final int MAX_LENGTH = 256;

	private SafeRequestPath() {
	}

	public static String of(URI uri) {
		if (uri == null) {
			return "/";
		}
		String path = uri.getRawPath();
		if (path == null || path.isBlank()) {
			return "/";
		}
		if (path.length() > MAX_LENGTH) {
			return path.substring(0, MAX_LENGTH);
		}
		return path;
	}
}
