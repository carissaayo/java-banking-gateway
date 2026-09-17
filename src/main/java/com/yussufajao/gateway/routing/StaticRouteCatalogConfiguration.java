package com.yussufajao.gateway.routing;

import com.yussufajao.gateway.config.GatewayUpstreamsProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class StaticRouteCatalogConfiguration {

	public static final String LEDGER_WRITE = "ledger-write";
	public static final String TRANSACTION_QUERY = "transaction-query";
	public static final String CUSTOMER = "customer";
	public static final String OPERATIONS = "operations";

	@Bean
	RouteLocator staticRouteCatalog(RouteLocatorBuilder builder, GatewayUpstreamsProperties upstreams) {
		String ledgerUri = upstreams.ledger().toString();
		String customerUri = upstreams.customer().toString();

		return builder.routes()
				.route(LEDGER_WRITE, route -> route
						.path("/api/ledger/accounts/**", "/api/ledger/transfers/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)
						.filters(filters -> filters
								.stripPrefix(2)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, LEDGER_WRITE))
						.uri(ledgerUri))
				.route(TRANSACTION_QUERY, route -> route
						.path("/api/transactions/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD)
						.filters(filters -> filters
								.stripPrefix(1)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, TRANSACTION_QUERY))
						.uri(ledgerUri))
				.route(CUSTOMER, route -> route
						.path("/api/customers/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PATCH)
						.filters(filters -> filters
								.stripPrefix(1)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, CUSTOMER))
						.uri(customerUri))
				.route(OPERATIONS, route -> route
						.path("/api/operations/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)
						.filters(filters -> filters
								.stripPrefix(1)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, OPERATIONS))
						.uri(customerUri))
				.build();
	}
}
