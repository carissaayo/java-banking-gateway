package com.yussufajao.gateway.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.yussufajao.gateway.routing.StaticRouteCatalogConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class GatewayRetryCatalogTest {

	private final GatewayRetryCatalog catalog = new GatewayRetryCatalog();

	@Test
	void writesAreNeverInTheRetryMethodSet() {
		assertThat(catalog.forRoute(StaticRouteCatalogConfiguration.LEDGER_WRITE).methods())
				.containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.HEAD)
				.doesNotContain(HttpMethod.POST);
		assertThat(catalog.forRoute(StaticRouteCatalogConfiguration.CUSTOMER).methods())
				.doesNotContain(HttpMethod.POST, HttpMethod.PATCH);
		assertThat(catalog.forRoute(StaticRouteCatalogConfiguration.TRANSACTION_QUERY).maxRetries())
				.isEqualTo(2);
	}
}