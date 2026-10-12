package com.yussufajao.gateway.hardening;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;


public class RequestSizeCatlog{

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final long NO_BODY= 0L;
    private static final long SMALL_JSON = 16 * 1024L;

    private final List<Rule> rules = List.of(
        rule(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/api/**", NO_BODY),
        rule(Set.of(HttpMethod.POST, HttpMethod.PATCH), "/api/**", SMALL_JSON));

    public Optional<Long> maxBodyBytes(HttpMethod method, String rawPath){
        if(method == null || rawPath == null || rawPath.isBlank()){
            return Optional.empty();
        }
        PathContainer path= PathContainer.parsePath(rawPath);
        for (Rule rule : rules){
            if(rule.methods().contains(method) && rule.pattern().matches(path)){
                return Optional.of(rule.maxBodyBytes());
            }
        }
        return Optional.empty();
    }

    private static Rule rule(Set<HttpMethod> methods, String path, long maxBytes){
        return new Rule(Set.copyOf(methods), PARSER.parse(path), maxBytes);
    }

    private record Rule(Set<HttpMethod> methods, PathPattern pattern, long maxBytes){}
}