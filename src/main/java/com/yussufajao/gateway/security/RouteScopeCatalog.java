package com.yussufajao.gateway.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class RouteScopeCatalog {
    
    private static final PathPatternParser PARSER = new PathPatternParser();

    private final List<Rule> rules = List.of(
            rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/ledger/accounts/**", "ledger.accounts.read"),
			rule(Set.of(HttpMethod.POST), "/api/ledger/accounts/**", "ledger.accounts.write"),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/ledger/transfers/**", "ledger.transfers.read"),
			rule(Set.of(HttpMethod.POST), "/api/ledger/transfers/**", "ledger.transfers.write"),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/transactions/**", "transactions.read"),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/customers/**", "customers.read"),
			rule(Set.of(HttpMethod.POST, HttpMethod.PATCH), "/api/customers/**", "customers.write"),
			rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/operations/**", "operations.read"),
			rule(Set.of(HttpMethod.POST), "/api/operations/**", "operations.admin"));

    public Optional<String> requiredScope(HttpMethod method, String rawPath){
        if (method == null || rawPath == null || rawPath.isBlank()){
            return Optional.empty();
        }
        PathContainer path = PathContainer.parsePath(rawPath);
        for(Rule rule : rules){
            if (rule.methods().contains(method) && rule.pattern().matches(path)){
                return Optional.of(rule.scope());
            }
        }
        return Optional.empty();
    }

    private static Rule rule(Set<HttpMethod> methods, String path, String scope){
        return new Rule(Set.copyOf(methods), PARSER.parse(path), scope);
    }

    private record Rule(Set<HttpMethod> methods, PathPattern pattern, String scope){

    }

}
