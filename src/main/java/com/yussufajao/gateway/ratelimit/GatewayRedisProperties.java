package com.yussufajao.gateway.ratelimit;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.redis")
public record GatewayRedisProperties(@NotNull URI uri) {
    public GatewayRedisProperties{
        if(uri == null){
            throw new IllegalArgumentException("gateway.redis.uri must be set");
        }
        String scheme = uri.getScheme();
        if (!"redis".equalsIgnoreCase(scheme) && !"rediss".equalsIgnoreCase(scheme)) {
			throw new IllegalArgumentException("gateway.redis.uri must be a redis or rediss URI");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException("gateway.redis.uri must include a host");
		}
    }
}
