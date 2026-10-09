package com.yussufajao.gateway.resilience;

import com.yussufajao.gateway.routing.StaticRouteCatalogConfiguration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GatewayRetryCatalog{

    private static final RouteRetryPolicy TIGHT_READS = RouteRetryPolicy.reads(1);
    private static final RouteRetryPolicy QUERY_READS = RouteRetryPolicy.reads(2);

    private final Map<String, RouteRetryPolicy> byRouteId = Map.of(
            StaticRouteCatalogConfiguration.LEDGER_WRITE, TIGHT_READS,
			StaticRouteCatalogConfiguration.TRANSACTION_QUERY, QUERY_READS,
			StaticRouteCatalogConfiguration.CUSTOMER, TIGHT_READS,
			StaticRouteCatalogConfiguration.OPERATIONS, TIGHT_READS);

    public RouteRetryPolicy forRoute(String routeId){
        RouteRetryPolicy policy = byRouteId.get(routeId);
        if(policy == null){
            throw new IllegalArgumentException("no retry policy for route " + routeId);
		}
		return policy;
    }

}