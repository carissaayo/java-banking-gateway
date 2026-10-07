package com.yussufajao.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class BearerTokenQueryRejectorTest {

	@Test
	void rejectsAccessTokenQueryParameter() {
		assertThat(BearerTokenQueryRejector.hasTokenInQuery(query("access_token", "secret"))).isTrue();
	}

	@Test
	void rejectsEmptyAccessTokenQueryParameter() {
		assertThat(BearerTokenQueryRejector.hasTokenInQuery(query("access_token", ""))).isTrue();
	}

	@Test
	void rejectsIdTokenQueryParameter() {
		assertThat(BearerTokenQueryRejector.hasTokenInQuery(query("id_token", "secret"))).isTrue();
	}

	@Test
	void allowsOtherQueryParameters() {
		assertThat(BearerTokenQueryRejector.hasTokenInQuery(query("accountId", "acc-1"))).isFalse();
	}

	private static MultiValueMap<String, String> query(String name, String value) {
		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add(name, value);
		return params;
	}
}
