package com.yussufajao.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SafeRequestPathTest {

	@Test
	void omitsQueryStringAndCapsLength() {
		assertThat(SafeRequestPath.of(URI.create("/api/ledger/accounts/acc-1?token=secret")))
				.isEqualTo("/api/ledger/accounts/acc-1");
		assertThat(SafeRequestPath.of(URI.create("/" + "a".repeat(300)))).hasSize(256);
		assertThat(SafeRequestPath.of(null)).isEqualTo("/");
	}
}
