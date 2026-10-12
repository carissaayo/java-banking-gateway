@Test
void doesNotForwardSpoofedXForwardedForToLedger() {
	ledger.stubFor(get(urlEqualTo("/accounts/acc-1"))
			.willReturn(okJson("{\"id\":\"acc-1\"}")));

	webTestClient.get()
			.uri("/api/ledger/accounts/acc-1")
			.header(HttpHeaders.AUTHORIZATION, TestJwtTokens.bearer("ledger.accounts.read"))
			.header("X-Forwarded-For", "8.8.8.8")
			.header(HttpHeaders.CONNECTION, "close")
			.exchange()
			.expectStatus().isOk();

	ledger.verify(getRequestedFor(urlEqualTo("/accounts/acc-1"))
			.withoutHeader("X-Forwarded-For")
			.withoutHeader("Connection"));
}