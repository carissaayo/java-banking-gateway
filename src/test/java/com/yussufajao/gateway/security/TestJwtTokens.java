package com.yussufajao.gateway.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class TestJwtTokens {

	public static final String ISSUER = "http://127.0.0.1:8180/realms/banking";
	public static final String AUDIENCE = "banking-gateway";

	static final RSAKey RSA_KEY = generateRsa();

	private TestJwtTokens() {
	}

	public static String bearer(String... scopes) {
		return "Bearer " + rs256(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), scopes);
	}

	public static String bearerFor(String clientId, String subject, String... scopes) {
		return "Bearer " + rs256For(clientId, subject, scopes);
	}

	public static String expired() {
		return rs256(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(60), "ledger.accounts.read");
	}

	public static String wrongIssuer() {
		return rs256("http://127.0.0.1:8180/realms/other", List.of(AUDIENCE), Instant.now().plusSeconds(300),
				"ledger.accounts.read");
	}

	public static String wrongAudience() {
		return rs256(ISSUER, List.of("some-other-api"), Instant.now().plusSeconds(300), "ledger.accounts.read");
	}

	public static String hs256() {
		try {
			byte[] secret = "01234567890123456789012345678901".getBytes();
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), validClaims("ledger.accounts.read"));
			jwt.sign(new MACSigner(secret));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not sign HS256 JWT", ex);
		}
	}

	public static String algNone() {
		return new PlainJWT(validClaims("ledger.accounts.read")).serialize();
	}

	static String rs256(String issuer, List<String> audience, Instant exp, String... scopes) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(issuer)
					.audience(audience)
					.expirationTime(Date.from(exp))
					.issueTime(Date.from(Instant.now().minusSeconds(5)))
					.subject("user-1")
					.claim("scope", String.join(" ", scopes))
					.build();
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256)
							.type(JOSEObjectType.JWT)
							.keyID(RSA_KEY.getKeyID())
							.build(),
					claims);
			jwt.sign(new RSASSASigner(RSA_KEY));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not sign test JWT", ex);
		}
	}

	private static String rs256For(String clientId, String subject, String... scopes) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(ISSUER)
					.audience(AUDIENCE)
					.expirationTime(Date.from(Instant.now().plusSeconds(300)))
					.issueTime(Date.from(Instant.now().minusSeconds(5)))
					.subject(subject)
					.claim("azp", clientId)
					.claim("scope", String.join(" ", scopes))
					.build();
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256)
							.type(JOSEObjectType.JWT)
							.keyID(RSA_KEY.getKeyID())
							.build(),
					claims);
			jwt.sign(new RSASSASigner(RSA_KEY));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not sign test JWT", ex);
		}
	}

	private static JWTClaimsSet validClaims(String... scopes) {
		return new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.audience(AUDIENCE)
				.expirationTime(Date.from(Instant.now().plusSeconds(300)))
				.issueTime(Date.from(Instant.now()))
				.subject("user-1")
				.claim("scope", String.join(" ", scopes))
				.build();
	}

	private static RSAKey generateRsa() {
		try {
			return new RSAKeyGenerator(2048).keyID("test-key").algorithm(JWSAlgorithm.RS256).generate();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not generate test RSA key", ex);
		}
	}
}
