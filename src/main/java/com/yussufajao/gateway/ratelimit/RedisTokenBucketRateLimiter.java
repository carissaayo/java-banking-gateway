package com.yussufajao.gateway.ratelimit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisTokenBucketRateLimiter implements RateLimiter{
    private static final String SCRIPT = """
			local tokens_key = KEYS[1]
			local timestamp_key = KEYS[2]
			local rate = tonumber(ARGV[1])
			local capacity = tonumber(ARGV[2])
			local now = tonumber(ARGV[3])
			local requested = tonumber(ARGV[4])
			local fill_time = capacity / rate
			local ttl = math.floor(fill_time * 2)
			local last_tokens = tonumber(redis.call("get", tokens_key))
			if last_tokens == nil then
			  last_tokens = capacity
			end
			local last_refreshed = tonumber(redis.call("get", timestamp_key))
			if last_refreshed == nil then
			  last_refreshed = 0
			end
			local delta = math.max(0, now - last_refreshed)
			local filled_tokens = math.min(capacity, last_tokens + (delta * rate))
			local allowed = filled_tokens >= requested
			local new_tokens = filled_tokens
			if allowed then
			  new_tokens = filled_tokens - requested
			end
			redis.call("setex", tokens_key, ttl, new_tokens)
			redis.call("setex", timestamp_key, ttl, now)
			return { allowed and 1 or 0, new_tokens }
			""";

    private final ReactiveStringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RedisTokenBucketRateLimiter(ReactiveStringRedisTemplate redis){
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(SCRIPT);
        this.script.setResultType(List.class);
    }

    @Override
    public Mono<RateLimitDecision> consume(String key, RateLimitPolicy policy){
        List<String> keys = List.of(key + ":tokens", key + ":ts");
        long now = Instant.now().getEpochSecond();
        return redis.execute(
            script,
            keys,
            String.valueOf(policy.replenishRate()),
            String.valueOf(policy.burstCapacity()),
            String.valueOf(now),
            "1"
        )
        .next()
        .map(result -> {
            List<?> values = (List<?>) result;
            boolean allowed = ((Number) values.get(0)).intValue() == 1;
            int remaining = Math.max(0, ((Number) values.get(1)).intValue());
            return allowed 
            ? RateLimitDecision.allow(remaining, policy)
            : RateLimitDecision.deny(remaining, policy);
        })
    }
}
