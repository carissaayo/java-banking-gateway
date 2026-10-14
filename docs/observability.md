# Observability

Phase 8. Metrics are counts over time. Access logs and `X-Correlation-ID` / `traceparent` remain the per-request story. Do not put account ids, subjects, or raw paths on metric labels.

W3C `traceparent` and `tracestate` are already accepted, sanitized, and forwarded by `CorrelationWebFilter`. That *is* OpenTelemetry context propagation. This phase does not add a second tracer or an OTLP exporter (those would double-write span ids and need a collector at boot).

`/actuator/prometheus` is exposed and **requires a JWT**. `/actuator/health` stays public. `/actuator/env` and other operator endpoints stay unexposed.

## Scrape (local)

```bash
curl.exe -s http://127.0.0.1:8080/actuator/prometheus ^
  -H "Authorization: Bearer %TOKEN%"
```

Expect `401` without a token. Expect Prometheus text with `jvm_memory_used_bytes` when authenticated.

## Series

| Name | Labels (low cardinality) | Meaning |
| --- | --- | --- |
| `spring_cloud_gateway_requests_seconds` | `routeId`, `httpMethod`, `status`, `outcome` | Gateway request rate and latency by catalog route |
| `http_server_requests_seconds` | method, status (uri/path tags stripped) | HTTP server timer |
| `gateway_rate_limit_total` | `policy`, `decision` (`allowed` / `exceeded` / `fail-open` / `fail-closed`) | Redis token-bucket outcomes |
| `gateway_auth_failures_total` | `code` | 401 invalid-credentials, 403 insufficient-scope |
| `gateway_policy_rejections_total` | `code` | Size, envelope, idempotency, route-not-found, … |
| `gateway_upstream_failures_total` | `code` | timeout, unavailable, circuit-open, upstream-error |
| `gateway_internal_errors_total` | none | Unclassified gateway 500s |
| `resilience4j_circuitbreaker_*` | `name` = `ledger` / `customer` / `operations` | Breaker state and rejected calls |
| `reactor_netty_*` | connection pool / event loop | Saturation |

Retry attempts are **not** a separate counter. Spring Cloud Gateway retries GET/HEAD only; extra upstream calls show up in duration and upstream failure series. Transfer POST is never retried.

## PromQL

Request rate by route:

```promql
sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count[5m]))
```

Error rate (5xx):

```promql
sum(rate(spring_cloud_gateway_requests_seconds_count{status=~"5.."}[5m]))
/
sum(rate(spring_cloud_gateway_requests_seconds_count[5m]))
```

p99 gateway latency:

```promql
histogram_quantile(0.99, sum by (le, routeId) (rate(spring_cloud_gateway_requests_seconds_bucket[5m])))
```

Auth failures:

```promql
sum by (code) (rate(gateway_auth_failures_total[5m]))
```

Rate-limit rejects and Redis fail-closed:

```promql
sum by (policy, decision) (rate(gateway_rate_limit_total{decision=~"exceeded|fail-closed"}[5m]))
```

Circuit open (ledger):

```promql
resilience4j_circuitbreaker_state{name="ledger",state="open"}
```

Upstream timeouts:

```promql
sum by (code) (rate(gateway_upstream_failures_total[5m]))
```

Import `docs/grafana/gateway-dashboard.json` into Grafana with a Prometheus datasource named `Prometheus`.

## Alerts (start here)

| Alert | Expr | For | Why |
| --- | --- | --- | --- |
| Gateway5xx | error-rate PromQL `> 0.05` | 5m | Edge is failing clients |
| LedgerCircuitOpen | `resilience4j_circuitbreaker_state{name="ledger",state="open"} == 1` | 1m | Transfer path is 503 |
| RateLimitFailClosed | `increase(gateway_rate_limit_total{decision="fail-closed"}[10m]) > 0` | 0m | Redis is down on a write policy |
| AuthFailureSpike | `sum(rate(gateway_auth_failures_total[5m])) > 1` | 10m | Bad tokens or a scrape/scanner |
| UpstreamTimeouts | `sum(rate(gateway_upstream_failures_total{code="upstream-timeout"}[5m])) > 0.1` | 5m | Ledger/customer too slow |

Tune thresholds after Phase 9 load tests. Do not alert on a single 401 from `/actuator/prometheus` without a token.
