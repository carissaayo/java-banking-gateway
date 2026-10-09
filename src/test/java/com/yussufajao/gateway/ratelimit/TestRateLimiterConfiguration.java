package com.yussufajao.gateway.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

@Configuration
@Profile("test")
public class TestRateLimiterConfiguration {

	@Bean
	RateLimiter rateLimiter() {
		return (key, policy) -> Mono.just(
				RateLimitDecision.allow(policy.burstCapacity() - 1, policy));
	}
}
