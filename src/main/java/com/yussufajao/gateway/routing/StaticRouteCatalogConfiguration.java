package com.yussufajao.gateway.routing;

import java.util.Set;
import com.yussufajao.gateway.config.GatewayUpstreamsProperties;
import com.yussufajao.gateway.resilience.GatewayRetryCatalog;
import com.yussufajao.gateway.resilience.GatewayTimeoutCatalog;
import com.yussufajao.gateway.resilience.RouteTimeout;
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
	RouteLocator staticRouteCatalog(RouteLocatorBuilder builder, GatewayUpstreamsProperties upstreams, GatewayTimeoutCatalog timeouts, GatewayRetryCatalog retries) {
		String ledgerUri = upstreams.ledger().toString();
		String customerUri = upstreams.customer().toString();
		RouteTimeout ledgerTimeout = timeouts.forRoute(LEDGER_WRITE);
		RouteTimeout transactionTimeout = timeouts.forRoute(TRANSACTION_QUERY);
		RouteTimeout customerTimeout = timeouts.forRoute(CUSTOMER);
		RouteTimeout operationsTimeout = timeouts.forRoute(OPERATIONS);

		RouteRetryPolicy ledgerRetry= retries.forRoute(LEDGER_WRITE);
		RouteRetryPolicy transactionRetry = retries.forRoute(TRANSACTION_QUERY);
		RouteRetryPolicy customerRetry = retries.forRoute(CUSTOMER);
		RouteRetryPolicy operationsRetry = retries.forRoute(OPERATIONS);

		return builder.routes()
				.route(LEDGER_WRITE, route -> route
						.path("/api/ledger/accounts/**", "/api/ledger/transfers/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)
						.filters(filters -> filters
								.stripPrefix(2)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, LEDGER_WRITE)
								.circuitBreaker(cb -> cb
									.setName("ledger")
									.setStatusCodes(Set.of("500", "502", "503", "504")))
								.retry(retry -> retry
									.setRetries(ledgerRetry.maxRetries())
									.setMethods(ledgerRetry.methods())
									.setSeries(Set.of())
									.setExceptions(Set.of(
										java.io.IOException.class,
										java.util.concurrent.TimeoutException.class,
										io.netty.handler.timeout.TimeoutException.class
									))

								)
							)
						.metadata("connect-timeout", ledgerTimeout.connectMillis())
						.metadata("response-timeout", ledgerTimeout.response())
						.uri(ledgerUri))
				.route(TRANSACTION_QUERY, route -> route
						.path("/api/transactions/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD)
						.filters(filters -> filters
								.stripPrefix(1)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, TRANSACTION_QUERY)
								.circuitBreaker(cb -> cb
									.setName("ledger")
									.setStatusCodes(Set.of("500", "502", "503", "504")))
								.retry(retry -> retry
									.setRetries(transactionRetry.maxRetries())
									.setMethods(transactionRetry.methods())
									.setSeries(Set.of())
									.setExceptions(Set.of(
										java.io.IOException.class,
										java.util.concurrent.TimeoutException.class,
										io.netty.handler.timeout.TimeoutException.class
									))

								)
							)
						.metadata("connect-timeout", transactionTimeout.connectMillis())
						.metadata("response-timeout", transactionTimeout.response())
						.uri(ledgerUri))
				.route(CUSTOMER, route -> route
						.path("/api/customers/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PATCH)
						.filters(filters -> filters
								.stripPrefix(1)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, CUSTOMER)
								.circuitBreaker(cb -> cb
									.setName("customer")
									.setStatusCodes(Set.of("500", "502", "503", "504")))
								.retry(retry -> retry
									.setRetries(customerRetry.maxRetries())
									.setMethods(customerRetry.methods())
									.setSeries(Set.of())
									.setExceptions(Set.of(
										java.io.IOException.class,
										java.util.concurrent.TimeoutException.class,
										io.netty.handler.timeout.TimeoutException.class
									))

								)
							)
						.metadata("connect-timeout", customerTimeout.connectMillis())
						.metadata("response-timeout", customerTimeout.response())
						.uri(customerUri))
				.route(OPERATIONS, route -> route
						.path("/api/operations/**")
						.and()
						.method(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)
						.filters(filters -> filters
								.stripPrefix(1)
								.addResponseHeader(GatewayHeaders.ROUTE_ID, OPERATIONS)
								.circuitBreaker(cb -> cb
									.setName("operations")
									.setStatusCodes(Set.of("500", "502", "503", "504")))
								.retry(retry -> retry
									.setRetries(operationsRetry.maxRetries())
									.setMethods(operationsRetry.methods())
									.setSeries(Set.of())
									.setExceptions(Set.of(
										java.io.IOException.class,
										java.util.concurrent.TimeoutException.class,
										io.netty.handler.timeout.TimeoutException.class
									))
								)
							)
						.metadata("connect-timeout", operationsTimeout.connectMillis())
						.metadata("response-timeout", operationsTimeout.response())
						.uri(customerUri))
				.build();
	}
}
