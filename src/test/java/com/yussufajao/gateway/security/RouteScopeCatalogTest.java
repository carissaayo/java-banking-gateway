package com.yussufajao.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

class RouteScopeCatalogTest {

	private final RouteScopeCatalog catalog = new RouteScopeCatalog();

	@ParameterizedTest
	@CsvSource({
			"GET, /api/ledger/accounts/acc-1, ledger.accounts.read",
			"HEAD, /api/ledger/accounts/acc-1, ledger.accounts.read",
			"POST, /api/ledger/accounts, ledger.accounts.write",
			"GET, /api/ledger/transfers/tx-1, ledger.transfers.read",
			"HEAD, /api/ledger/transfers/tx-1, ledger.transfers.read",
			"POST, /api/ledger/transfers, ledger.transfers.write",
			"GET, /api/transactions/txn-1, transactions.read",
			"HEAD, /api/transactions/txn-1, transactions.read",
			"GET, /api/customers/cus-1, customers.read",
			"HEAD, /api/customers/cus-1, customers.read",
			"POST, /api/customers, customers.write",
			"PATCH, /api/customers/cus-1, customers.write",
			"GET, /api/operations/status, operations.read",
			"HEAD, /api/operations/status, operations.read",
			"POST, /api/operations/status, operations.admin"
	})
	void mapsMethodAndPathToScope(String method, String path, String scope) {
		assertThat(catalog.requiredScope(HttpMethod.valueOf(method), path)).contains(scope);
	}

	@Test
	void transferReadIsNotAccountRead() {
		assertThat(catalog.requiredScope(HttpMethod.GET, "/api/ledger/transfers/tx-1").orElseThrow())
				.isEqualTo("ledger.transfers.read");
	}

	@Test
	void unknownPathHasNoScopeRow() {
		assertThat(catalog.requiredScope(HttpMethod.GET, "/api/does-not-exist")).isEmpty();
	}
}
