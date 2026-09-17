# Phase 0 — Threat model and policies

This document is the security and resilience contract for the MVP routes. Phase 1 implements only routing against this catalog. Later phases add the controls named here; they do not invent new routes ad hoc.

## 1. Clients

| Client | How it authenticates (Phase 3+) | Typical traffic |
| --- | --- | --- |
| Customer application | OAuth2 authorization-code or equivalent user access token | Account reads, transfer writes, transaction history |
| Operations user | OAuth2 access token with privileged scopes | Operations reads and limited administration |
| Internal service | OAuth2 client credentials | Machine-to-machine reads and explicit writes |
| External partner | Client credentials now; optional mTLS later | Contracted partner APIs only |

The gateway treats every client as untrusted until a token is validated. Downstream services still authorize the specific account, customer, or transfer. Edge authentication is not business authorization.

## 2. Trust boundaries

```text
Public clients
    │  TLS (required in production)
    ▼
Spring Cloud Gateway     ← this process
    │  validates JWT locally from cached JWKS (Phase 3)
    │  consumes Redis tokens (Phase 4)
    │  applies timeout / circuit / retry (Phases 5–6)
    ▼
Internal upstreams: ledger, transaction query, customer, operations
    │
Identity provider (Keycloak locally) — JWKS only, not a call per request
Redis — rate-limit counters, never tokens or PII
```

Do not place the gateway on a public network without TLS termination in front of it or on it. Do not expose management endpoints on the public listener in production.

## 3. Identity

Documented now; enforced in Phase 3.

| Claim | Policy |
| --- | --- |
| Issuer | Exact match to the configured issuer. No silent default. |
| Audience | Exact match to the gateway API audience. |
| Algorithm | Asymmetric, allow-listed (RS256 or ES256). Reject `none` and HMAC unless a future internal-only profile explicitly requires it. |
| Expiry / nbf | Required. |
| Scope | Route-level allow-list below. Missing or extra-unrelated scopes do not grant access. |
| Token location | `Authorization: Bearer`. Reject tokens in query strings. |

Local Keycloak clients (to be created in Phase 3): customer, operations, and a service client using client credentials. Access tokens must be short-lived.

## 4. Data that must never be logged

- `Authorization` headers and raw access tokens
- Cookies
- Passwords, OTPs, and refresh tokens
- Full account numbers (PAN-like or IBAN-like values)
- Request and response bodies by default
- Unfiltered query strings (they can contain tokens or account identifiers)
- Redis keys built from tokens, API keys, or personal names

Allowed identifiers: correlation ID, trace IDs, OAuth2 `client_id`, opaque subject IDs, route ID, HTTP method, path template, status, duration, rate-limit decision, circuit state.

## 5. Route catalog

Unknown paths have no route. They must fail closed (HTTP 404 in Phase 1; RFC 9457 problem details in Phase 2). Unlisted methods on a known path also fail closed.

`Idempotency-Key` is forwarded unchanged when present. The gateway never mints business idempotency.

Path rewriting strips the public `/api/...` prefix so upstreams keep a stable internal path. Clients never see upstream hostnames.

### ledger-write

| Field | Policy |
| --- | --- |
| Public paths | `/api/ledger/accounts/**`, `/api/ledger/transfers/**` |
| Upstream | Ledger service. Rewritten to `/accounts/**` and `/transfers/**` |
| Methods | `GET`, `HEAD`, `POST` |
| Scopes | `GET`/`HEAD` accounts: `ledger.accounts.read`. `POST` accounts: `ledger.accounts.write`. `GET`/`HEAD` transfers: `ledger.transfers.read`. `POST` transfers: `ledger.transfers.write`. Reversals (later path) require `ledger.reversals.write`. |
| Timeout | Short. Tighter than the client timeout. Transfer writes must not wait indefinitely. |
| Rate limit | Accounts read: moderate steady rate, small burst. Transfer writes: low steady rate, very small burst. Keyed by client ID + subject + route. |
| Redis failure | Fail closed for transfer writes. Fail closed or tightly bounded local emergency policy for account writes. |
| Circuit breaker | Dedicated breaker for the ledger upstream. Open → 503 problem response. |
| Retry | `GET`/`HEAD` may retry connection failures before the request is known to have reached the upstream. `POST` transfers, payments, withdrawals, reversals, and settlements are **not** retried after an ambiguous timeout. |
| Max request size | Small JSON envelope. Reject oversized bodies before proxying. |
| Idempotency key | Required for `POST /transfers` at the downstream. Gateway forwards it; it does not invent one. |
| Headers | Forward `Authorization` after validation, `Idempotency-Key`, correlation and W3C trace headers. Remove hop-by-hop headers. Do not forward untrusted `X-Forwarded-*` except from configured proxies. |

### transaction-query

| Field | Policy |
| --- | --- |
| Public path | `/api/transactions/**` |
| Upstream | Ledger mock in Phase 1; dedicated query service later. Rewritten to `/transactions/**` |
| Methods | `GET`, `HEAD` |
| Scopes | `transactions.read` |
| Timeout | Moderate read budget. |
| Rate limit | Moderate steady rate, modest burst. Keyed by client ID + subject + route. |
| Redis failure | Documented fail-open is acceptable for this read-only route; every degraded decision must be recorded. |
| Circuit breaker | Same ledger/query upstream group as configured later. |
| Retry | Safe. `GET`/`HEAD` only, bounded attempts. |
| Max request size | Negligible; GET has no body. |
| Idempotency key | Not applicable. |
| Headers | Same forwarding/removal rules as ledger-write, without requiring `Idempotency-Key`. |

### customer

| Field | Policy |
| --- | --- |
| Public path | `/api/customers/**` |
| Upstream | Customer service. Rewritten to `/customers/**` |
| Methods | `GET`, `HEAD`, `POST`, `PATCH` |
| Scopes | Reads: customer-read scope owned by the customer API (to be named with the customer service). Writes: matching write scope. Operations users do not use this route for privileged admin work. |
| Timeout | Moderate. |
| Rate limit | Customer-read: moderate + burst. Writes: lower than reads. |
| Redis failure | Fail closed for writes. Fail-open may be acceptable for reads. |
| Circuit breaker | Dedicated customer-upstream breaker. |
| Retry | `GET`/`HEAD` only. `POST`/`PATCH` are not retried after ambiguous timeout. |
| Max request size | Small JSON. |
| Idempotency key | Required for `POST` customer-create if the downstream contract says so; forwarded only. |
| Headers | Same sanitization rules. Never log customer names or government IDs from query strings. |

### operations

| Field | Policy |
| --- | --- |
| Public path | `/api/operations/**` |
| Upstream | Customer mock in Phase 1; operations API later. Rewritten to `/operations/**` |
| Methods | `GET`, `HEAD`, `POST` |
| Scopes | `operations.read` for reads. `operations.admin` for mutating operations. |
| Timeout | Moderate, not unbounded. Admin tools still get a budget. |
| Rate limit | Stricter than customer-read. Privileged routes are not high-volume. |
| Redis failure | Fail closed. |
| Circuit breaker | Dedicated breaker. |
| Retry | `GET`/`HEAD` only. Admin `POST` is not retried automatically. |
| Max request size | Small JSON. |
| Idempotency key | Forwarded for `POST`. |
| Headers | Stronger audit in Phase 2+: client, subject, route, outcome. Still no bodies or tokens. |

## 6. Cross-cutting Phase 1 exceptions

Phase 1 does **not** enforce JWT, rate limits, timeouts, circuit breakers, or retries. Those absences are intentional so routing can be proven in isolation. Until Phase 3, business routes are reachable without a token and must not be exposed beyond local development.

Phase 1 still honors:

- Static routes only; unmatched paths fail
- Method allow-lists per route
- Request path rewriting
- Forwarding of `Idempotency-Key`
- Actuator limited to health/liveness/readiness
- Required upstream URIs at boot (`GATEWAY_UPSTREAMS_LEDGER`, `GATEWAY_UPSTREAMS_CUSTOMER`), with no silent production defaults
