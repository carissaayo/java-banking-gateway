package com.yussufajao.gateway.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.yussufajao.gateway.routing.StaticRouteCatalogConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GatewayTimeoutCatalogTest {

	private final GatewayTimeoutCatalog catalog = new GatewayTimeoutCatalog();

	@Test
	void ledgerIsShorterThanCustomerReads() {
		assertThat(catalog.forRoute(StaticRouteCatalogConfiguration.LEDGER_WRITE).response())
				.isEqualTo(Duration.ofMillis(2000));
		assertThat(catalog.forRoute(StaticRouteCatalogConfiguration.CUSTOMER).response())
				.isEqualTo(Duration.ofMillis(4000));
	}
}