# Architecture and failure modes

The gateway is the edge. It authenticates, throttles, bounds upstream time, and writes an access line. It does **not** decide that a transfer is correct. That stays in the ledger.

```text
Client  ->  Gateway (JWT, scope, quota, size, CORS, correlation)
        ->  Redis (token bucket)
        ->  Ledger / Customer mocks (timeouts, GET/HEAD retry on connection reset, circuit per upstream)
```

## What each knob does

| Failure | Gateway behaviour | What it is not |
| --- | --- | --- |
| Bad/missing JWT, wrong audience | 401 `invalid-credentials` | Downstream never sees the call |
| Wrong scope | 403 `insufficient-scope` | Not a 404 |
| Redis down on a write quota | 503 fail-closed (Redis consume bounded to 1s) | Not “let the transfer through”, not hang until the client times out |
| Redis down on a documented read quota | fail-open, decision logged (same 1s bound) | Not silent |
| Upstream slower than the route timeout | 504 `upstream-timeout` | GET retry on **connection reset** is tested; a slow stub may be cut by the circuit TimeLimiter first. POST/PATCH not retried |
| Nobody listening | 503 `upstream-unavailable` | Host/port must not appear in `detail` |
| Too many upstream 5xx | 503 `circuit-open` | Customer routes use a **different** breaker |
| Oversize body / bad envelope | 413 / 400 before routing | |
| Transfer without `Idempotency-Key` | 400 | Gateway never mints a key |
| SIGTERM | `server.shutdown=graceful`, 20s drain | New connections fail; in-flight may still finish upstream |

## Ports (do not shuffle for local demo)

| Process | Host port |
| --- | --- |
| Gateway | 8080 |
| Mock ledger | 8081 |
| Mock customer | 8082 |
| Keycloak | 8180 |
| Redis | 6379 |

Inside Compose, the gateway talks to **service names** (`mock-ledger:8080`, `keycloak:8080`, `redis:6379`). Tokens still carry `iss=http://127.0.0.1:8180/realms/banking`. The container therefore sets `GATEWAY_SECURITY_JWK_SET_URI` to Keycloak’s internal certs URL and keeps `GATEWAY_SECURITY_ISSUER_URI` as the public issuer. That is not a second identity provider.

On the host, the `local` profile uses issuer discovery (`http://127.0.0.1:8180/...`). Do **not** export `GATEWAY_SECURITY_JWK_SET_URI` into the host JVM.

## Production notes (not implemented here)

- **TLS**: terminate TLS in front of the gateway (or configure `server.ssl`). `HSTS` stays off until the process itself speaks HTTPS.
- **Trusted proxies**: `forwarded` / `x-forwarded` stay **disabled** until a real proxy hop is documented. Do not turn them on for “it made redirects nicer”.
- **Management**: `/actuator/prometheus` is JWT-gated on the public port for this demo. A bank would bind metrics to an internal `management.server.port` / network. `/actuator/env` is not exposed.
- **Secrets**: compose and `.env` are local demo only. Real deploys inject `GATEWAY_*` at boot; missing values fail startup.
- **Images**: CI builds the gateway image after `verify`, then Trivy fails the job on fixable **CRITICAL** findings (`ignore-unfixed: true`). Unfixed distro CVEs in Temurin/Keycloak are not a merge gate.

## How to walk a reviewer

1. `cp .env.example .env` then `docker compose up -d --build`
2. `bash scripts/demo.sh` — health, JWT, 401/403, 429, 504, no POST retry, circuit open/recover, correlation, Prometheus. GET retry on connection reset is `GatewayRetryIT`.
3. Optional: `http/gateway-demo.http` in the editor

`./mvnw verify` is the automated suite (in-process JWTs, no Keycloak).
