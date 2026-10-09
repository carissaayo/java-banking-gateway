package com.yussufajao.gateway.resilience;

import java.util.Set;
import org.springframework.http.HttpMethod;

public record RouteRetryPolicy(int maxRetries, Set<HttpMethod> methods){
    public RouteRetryPolicy{
        if (maxRetries < 1) {
			throw new IllegalArgumentException("maxRetries must be at least 1");
		}
		if (methods == null || methods.isEmpty()) {
			throw new IllegalArgumentException("retry methods must be set");
		}
		for (HttpMethod method : methods) {
			if (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT) {
				throw new IllegalArgumentException("writes must not be retried");
			}
		}
		methods = Set.copyOf(methods);
    }

    public static RouteRetryPolicy reads(int maxRetries) {
		return new RouteRetryPolicy(maxRetries, Set.of(HttpMethod.GET, HttpMethod.HEAD));
	}
}