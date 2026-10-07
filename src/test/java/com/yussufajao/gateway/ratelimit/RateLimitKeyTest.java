package com.yussufajao.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitKeyTest {

	@Test
	void usesPolicyClientAndSubject() {
		assertThat(RateLimitKey.of("ledger-transfers-write", "customer-app", "sub-1"))
				.isEqualTo("rl:ledger-transfers-write:customer-app:sub-1");
	}

	@Test
	void blankPartsBecomeDash() {
		assertThat(RateLimitKey.of("customer-read", "", null)).isEqualTo("rl:customer-read:-:-");
	}

	@Test
	void replacesUnsafeKeyCharacters() {
		assertThat(RateLimitKey.of("p", "eyJ.abc/def+ghi=", "x"))
				.isEqualTo("rl:p:eyJ.abc_def_ghi_:x");
	}
}
