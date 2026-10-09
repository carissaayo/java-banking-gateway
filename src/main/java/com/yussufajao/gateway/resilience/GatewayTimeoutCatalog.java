package com.yussufajao.gateway.resilience;

import com.yussufajao.gateway.routing.StaticRouteCatalogConfiguration;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Map;

@Component()
public class GatewayTimeoutCatalog {
    private static final RouteTimeout LEDGER= new RouteTimeout(Duration.ofMillis(1000), Duration.ofMillis(2000));
    private static final RouteTimeout READS= new RouteTimeout(Duration.ofMillis(1000), Duration.ofMillis(4000));

    private final Map<String, RouteTimeout> byRouteId = Map.of(
        StaticRouteCatalogConfiguration.LEDGER_WRITE, LEDGER,
        StaticRouteCatalogConfiguration.TRANSFER_QUERY, READS,
        StaticRouteCatalogConfiguration.CUSTOMER, READS,
        StaticRouteCatalogConfiguration.OPERATIONS, READS);

    private RouteTimeout forRoute(String routeId){
        RouteTimeout timeout = byRouteId.get(routeId);
        if(timeout == null){
            throw new IllegalArgumentException("no timeout policy for route " + routeId);
		}
		return timeout;
    }
}
