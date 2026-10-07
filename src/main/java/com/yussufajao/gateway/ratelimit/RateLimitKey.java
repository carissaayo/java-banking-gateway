package com.yussufajao.gateway.ratelimit;


public final class RateLimitKey {
    
    private RateLimitKey() {
	}

    public static String of(String policyId, String clientId, String subject) {
		return "rl:" + token(policyId) + ":" + token(clientId) + ":" + token(subject);
	}

    private static String token(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
				out.append(c);
			} else {
				out.append('_');
			}
		}
		return out.toString();
	}
}
