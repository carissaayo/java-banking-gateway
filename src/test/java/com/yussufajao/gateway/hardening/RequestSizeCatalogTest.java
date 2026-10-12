package com.yussufajao.gateway.hardening;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RequestSizeCatalogTest {

	private final RequestSizeCatalog catalog = new RequestSizeCatalog();

	@Test
	void apiWritesAreSmallJsonAndGetsHaveNoBody() {
		assertThat(catalog.maxBodyBytes(HttpMethod.POST, "/api/ledger/transfers")).contains(16 * 1024L);
		assertThat(catalog.maxBodyBytes(HttpMethod.GET, "/api/ledger/accounts/acc-1")).contains(0L);
		assertThat(catalog.maxBodyBytes(HttpMethod.GET, "/actuator/health")).isEmpty();
	}
}
