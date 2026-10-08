package com.yussufajao.gateway.ratelimit;

public class RateLimitDecision {
    public static RateLimitDecision allow(int remaining, RateLimitPolicy policy) {
		return new RateLimitDecision(true, remaining, policy.burstCapacity(), policy.replenishRate());
	}
	public static RateLimitDecision deny(int remaining, RateLimitPolicy policy) {
		return new RateLimitDecision(false, remaining, policy.burstCapacity(), policy.replenishRate());
	}
}
