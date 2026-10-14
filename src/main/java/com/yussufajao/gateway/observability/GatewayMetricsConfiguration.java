package com.yussufajao.gateway.observability;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayMetricsConfiguration {

	@Bean
	MeterFilter denyHighCardinalityTags() {
		return MeterFilter.ignoreTags("uri", "path");
	}
}
