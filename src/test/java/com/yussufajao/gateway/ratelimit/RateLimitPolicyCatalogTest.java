package com.yussufajao.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RateLimitPolicyCatalogTest {

	private final RateLimitPolicyCatalog catalog = new RateLimitPolicyCatalog();

	@Test
	void transferWriteIsStricterAndFailClosed() {
		RateLimitPolicy policy = catalog.policyFor(HttpMethod.POST, "/api/ledger/transfers").orElseThrow();
		assertThat(policy.id()).isEqualTo("ledger-transfers-write");
		assertThat(policy.replenishRate()).isEqualTo(2);
		assertThat(policy.burstCapacity()).isEqualTo(4);
		assertThat(policy.failClosed()).isTrue();
	}

	@Test
	void transactionReadMayFailOpen() {
		assertThat(catalog.policyFor(HttpMethod.GET, "/api/transactions/txn-1").orElseThrow().failClosed())
				.isFalse();
	}

	@Test
	void accountReadIsNotTransferWrite() {
		assertThat(catalog.policyFor(HttpMethod.GET, "/api/ledger/accounts/acc-1").orElseThrow().id())
				.isEqualTo("ledger-accounts-read");
	}

	@Test
	void healthAndUnknownHaveNoPolicy() {
		assertThat(catalog.policyFor(HttpMethod.GET, "/actuator/health")).isEmpty();
		assertThat(catalog.policyFor(HttpMethod.GET, "/api/does-not-exist")).isEmpty();
	}
}
