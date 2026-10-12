package com.yussufajao.gateway.resilience;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class IdempotencyKeyCatalog{

    private static final PathPatternParser PARSER = new PathPatternParser();

    private final List<Rule> rules  = List.of(
        rule(Set.of(HttpMethod.POST), "/api/ledger/transfers/**"));

    public boolean required(HttpMethod method, String rawPath){
        if(method == null || rawPath == null || rawPath.isBlank()){
            return false;
        }
        PathContainer path = PathContainer.parsePath(rawPath);
        for(Rule rule : rules){
            if (rule.methods().contains(method) && rule.pattern().matches(path)){
                return true;
            }
        }
        return false;
    }

    private static Rule rule(Set<HttpMethod> methods, String path){
        return new Rule(Set.copyOf(methods), PARSER.parse(path));
    }

    private record Rule(Set<HttpMethod> methods, PathPattern pattern){
    }
}