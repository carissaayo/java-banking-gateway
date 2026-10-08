package com.yussufajao.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;

@SpringBootApplication(exclude= {
	RedisAutoConfiguration.class,
	RedisReactiveAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class BankingGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankingGatewayApplication.class, args);
	}
}
