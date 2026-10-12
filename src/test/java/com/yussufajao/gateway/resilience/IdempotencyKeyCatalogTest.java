package com.yussufajao.gateway.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class IdempotencyKeyCatalogTest {

	private final IdempotencyKeyCatalog catalog = new IdempotencyKeyCatalog();

	@Test
	void onlyTransferPostsRequireTheKey() {
		assertThat(catalog.required(HttpMethod.POST, "/api/ledger/transfers")).isTrue();
		assertThat(catalog.required(HttpMethod.GET, "/api/ledger/transfers/tx-1")).isFalse();
		assertThat(catalog.required(HttpMethod.POST, "/api/ledger/accounts")).isFalse();
		assertThat(catalog.required(HttpMethod.POST, "/api/customers")).isFalse();
	}
}
