package com.yussufajao.gateway.ratelimit;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;


public class RateLimitPolicyCatalog {
    
    private static final PathPatternParser PARSER = new PathPatternParser();

    private final List<Rule> rules = List.of(
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/ledger/accounts/**",
					new RateLimitPolicy("ledger-accounts-read", 10, 20, true)),
			rule(Set.of(HttpMethod.POST), "/api/ledger/accounts/**",
					new RateLimitPolicy("ledger-accounts-write", 5, 10, true)),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/ledger/transfers/**",
					new RateLimitPolicy("ledger-transfers-read", 5, 10, true)),
			rule(Set.of(HttpMethod.POST), "/api/ledger/transfers/**",
					new RateLimitPolicy("ledger-transfers-write", 2, 4, true)),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/transactions/**",
					new RateLimitPolicy("transaction-query", 10, 20, false)),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/customers/**",
					new RateLimitPolicy("customer-read", 10, 20, false)),
			rule(Set.of(HttpMethod.POST, HttpMethod.PATCH), "/api/customers/**",
					new RateLimitPolicy("customer-write", 5, 10, true)),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/operations/**",
					new RateLimitPolicy("operations-read", 5, 10, true)),
			rule(Set.of(HttpMethod.POST), "/api/operations/**",
					new RateLimitPolicy("operations-admin", 2, 4, true)));
	public Optional<RateLimitPolicy> policyFor(HttpMethod method, String rawPath) {
		if (method == null || rawPath == null || rawPath.isBlank()) {
			return Optional.empty();
		}
		PathContainer path = PathContainer.parsePath(rawPath);
		for (Rule rule : rules) {
			if (rule.methods().contains(method) && rule.pattern().matches(path)) {
				return Optional.of(rule.policy());
			}
		}
		return Optional.empty();
	}
	private static Rule rule(Set<HttpMethod> methods, String path, RateLimitPolicy policy) {
		return new Rule(Set.copyOf(methods), PARSER.parse(path), policy);
	}
	private record Rule(Set<HttpMethod> methods, PathPattern pattern, RateLimitPolicy policy) {
	}

}
