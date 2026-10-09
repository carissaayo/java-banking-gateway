package com.yussufajao.gateway.ratelimit;

import reactor.core.publisher.Mono;

public interface RateLimiter {

	Mono<RateLimitDecision> consume(String key, RateLimitPolicy policy);
}
