package com.yussufajao.gateway.resilience;

import java.time.Duration;

public record RouteTimeout(Duration connect, Duration response) {
    
    public RouteTimeout{
        if (connect == null || connect.isZero() || connect.isNegative()) {
			throw new IllegalArgumentException("connect timeout must be positive");
		}
		if (response == null || response.isZero() || response.isNegative()) {
			throw new IllegalArgumentException("response timeout must be positive");
		}
		if (response.compareTo(connect) < 0) {
			throw new IllegalArgumentException("response timeout must be >= connect timeout");
		}
    }
    
    public int connectMillis() {
		return Math.toIntExact(connect.toMillis());
	}
}
