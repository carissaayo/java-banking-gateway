package com.yussufajao.gateway.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
@Profile("!test") // so ./mvnw test never opens Redis
public class GatewayRedisConfiguration {
    
    @Bean
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory(GatewayRedisProperties redis){
        var uri = redis.uri();
        int port = uri.getPort() == -1 ? 6379 :uri.getPort();
        var standalone = new RedisStandaloneConfiguration(uri.getHost(), port);
        var factory = new LettuceConnectionFactory(standalone);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory){
        return new ReactiveStringRedisTemplate(factory);
    }
}
