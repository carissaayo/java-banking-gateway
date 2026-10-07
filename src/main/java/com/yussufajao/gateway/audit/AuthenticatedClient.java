package com.yussufajao.gateway.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedClient {

	private AuthenticatedClient() {
	}

	public static String clientId(Authentication authentication) {
		Jwt jwt = jwt(authentication);
		if (jwt == null) {
			return "";
		}
		String azp = jwt.getClaimAsString("azp");
		if (azp != null && !azp.isBlank()) {
			return azp;
		}
		String clientId = jwt.getClaimAsString("client_id");
		return clientId == null ? "" : clientId;
	}

	public static String subject(Authentication authentication) {
		Jwt jwt = jwt(authentication);
		if (jwt == null) {
			return "";
		}
		String subject = jwt.getSubject();
		return subject == null ? "" : subject;
	}

	private static Jwt jwt(Authentication authentication) {
		if (authentication instanceof JwtAuthenticationToken token) {
			return token.getToken();
		}
		return null;
	}
}
