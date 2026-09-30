# Phase 3 — OAuth2 / JWT security

Implementation contract. You write the code. This file is the checklist and the decisions. Phase 0 already chose the clients, claims, and scopes. Do not invent a second catalog.

**Exit criterion:** business routes deny by default. They accept only a valid JWT for the configured issuer and audience, with the scope required for that method and path. Health stays public. Tokens in query strings are rejected. Error bodies stay RFC 9457 and still never include `getMessage()`.

## 1. What this phase is

The gateway becomes an OAuth2 **resource server**. It does not issue tokens. Keycloak issues them. The gateway validates them locally from the issuer's JWKS and then checks a route-level scope.

It is still not business authorization. `ledger.transfers.write` means “this token may hit the transfer write path.” The ledger still decides whether *this* account can be debited.

## 2. What you must not do

- Do not call Keycloak (token introspection) on every request. Use JWKS + signature. Introspection is a later extension for opaque tokens.
- Do not silently default issuer, audience, or JWK set URI. Missing values fail boot. `application-local.yml` may set local Keycloak URLs because that profile is an explicit choice.
- Do not accept HMAC (`HS256`) or `alg: none`. Allow RS256. ES256 is optional if you add it to the allow-list and the realm actually signs with it.
- Do not read tokens from query parameters (`?access_token=`). `Authorization: Bearer` only.
- Do not log `Authorization`, cookies, raw tokens, or token payloads.
- Do not put Spring MVC on the classpath. Stay WebFlux. The resource-server starter must be the reactive one.
- Do not protect `/actuator/health`, `/actuator/health/liveness`, or `/actuator/health/readiness`. Everything else under `/api/**` requires authentication.
- Do not treat “has any scope” as enough. Extra unrelated scopes do not grant access.

## 3. Required configuration

Bind these. No placeholders that resolve to empty strings.

| Property | Env | Meaning |
| --- | --- | --- |
| `gateway.security.issuer-uri` | `GATEWAY_SECURITY_ISSUER_URI` | Exact `iss` claim. Also used to discover JWKS (`{issuer}/protocol/openid-connect/certs` on Keycloak). |
| `gateway.security.audience` | `GATEWAY_SECURITY_AUDIENCE` | Exact `aud` value the gateway accepts. Prefer a single API audience, e.g. `banking-gateway`. |

Validate at bind time:

- both are required
- issuer is `http` or `https` and has a host
- audience is a non-blank string

`local` profile example (not a production default):

```yaml
gateway:
  security:
    issuer-uri: http://127.0.0.1:8180/realms/banking
    audience: banking-gateway
```

Spring Security still needs the resource-server JWT settings. Point them at the same issuer so there is one source of truth. Do not keep a second, different issuer in `spring.security.oauth2.resourceserver.jwt`.

Readiness (Phase 0 / §17): once security is on, readiness should go down if signing keys cannot be loaded. Liveness must not depend on Keycloak.

## 4. Dependencies

Add the Boot 4 **reactive** OAuth2 resource-server starter (name it from the Boot 4.1 BOM; do not copy an old `spring-boot-starter-oauth2-resource-server` + MVC combo from a 2023 tutorial).

You will also need a JWT library in **tests** so CI can sign tokens without running Keycloak (Nimbus is the usual choice). Keep Keycloak for local demo, not for `./mvnw test` if you can avoid it.

## 5. Security filter chain

One `SecurityWebFilterChain` for WebFlux.

Suggested matchers:

| Path | Rule |
| --- | --- |
| `GET /actuator/health`, `GET /actuator/health/liveness`, `GET /actuator/health/readiness` | `permitAll` |
| `/api/**` | authenticated + method/path scope (below) |
| anything else | authenticated, then existing 404 problem handling |

**401 vs 404:** Security runs before routing. An anonymous call to `/api/does-not-exist` should be **401**, not 404. That is deny-by-default and it does not confirm whether the route exists. A valid token with no matching route remains **404** from Phase 2.

Map authentication and authorization failures to the Phase 2 problem envelope:

| Case | Status | `type` slug |
| --- | --- | --- |
| Missing / malformed / expired / wrong iss / wrong aud / bad sig / disallowed alg / token in query | 401 | `invalid-credentials` |
| Valid token, missing required scope | 403 | `insufficient-scope` |

`detail` stays a constant. Do not put “Jwt expired at …” or issuer URLs in the body.

Add `invalid-credentials` and `insufficient-scope` to `GatewayErrorCode`. Wire them through `GatewayProblemMapper` / the security exception handlers so `correlationId` is still present.

## 6. JWT validation

Validate all of:

- signature against JWKS
- `iss` exact match
- `aud` contains the configured audience (tokens may have multiple audiences)
- `exp` and `nbf`
- algorithm allow-list: RS256 (and ES256 only if you explicitly enable it)

Reject:

- missing `Authorization`
- anything other than `Bearer`
- `Authorization` plus `access_token` query param (reject even if the header is valid — the query copy is the leak)
- tokens whose `scope` / `scp` claim is absent when a route requires a scope

Claim-to-authority mapping:

- Read `scope` (space-separated, Keycloak default) and, if you want belt-and-suspenders, `scp`.
- Map each scope `ledger.accounts.read` to an authority `SCOPE_ledger.accounts.read` (Spring Security’s convention).
- Do not map realm roles such as `offline_access` into route access unless they appear in the Phase 0 table. Roles are not a substitute for the scope list below.

Optional on the access log (allowed identifiers from Phase 0): `client_id` and an opaque `sub`. Still no token, no name, no email.

## 7. Scope catalog (enforce this, not a coarser substitute)

Phase 0 split scopes by **method and path**, not by RouteLocator id. `ledger-write` is one Java route today and four scope rules. You must not authorize the whole route with a single scope.

| Method | Path | Required scope |
| --- | --- | --- |
| `GET`, `HEAD` | `/api/ledger/accounts/**` | `ledger.accounts.read` |
| `POST` | `/api/ledger/accounts/**` | `ledger.accounts.write` |
| `GET`, `HEAD` | `/api/ledger/transfers/**` | `ledger.transfers.read` |
| `POST` | `/api/ledger/transfers/**` | `ledger.transfers.write` |
| `GET`, `HEAD` | `/api/transactions/**` | `transactions.read` |
| `GET`, `HEAD` | `/api/customers/**` | `customers.read` |
| `POST`, `PATCH` | `/api/customers/**` | `customers.write` |
| `GET`, `HEAD` | `/api/operations/**` | `operations.read` |
| `POST` | `/api/operations/**` | `operations.admin` |

`ledger.reversals.write` stays reserved. Do not add a reversals path in this phase.

Phase 0 left customer scopes unnamed. They are now `customers.read` / `customers.write`. Update `docs/phase-0-policies.md` when you implement so the two docs do not drift.

Implementation choice (pick one, document it in an ADR):

1. **Preferred:** a method-aware authorization manager or gateway filter that looks up the table above. Keeps the Phase 1 `RouteLocator` ids.
2. **Also fine:** split `RouteLocator` into one route per scope row, then `hasAuthority("SCOPE_…")` on each. More YAML/Java, simpler Security DSL.

Unlisted method + path combinations stay denied (today they 404; with a token they still 404).

## 8. Token-in-query rejection

Do this before JWT decoding if you can.

- If the query string contains `access_token` or `id_token`, return 401 `invalid-credentials` and do not forward the request.
- Do not put the query in the access log (Phase 2 already uses `getRawPath()`).

## 9. Local Keycloak

Add a Keycloak service to `docker-compose.yml`. Suggested host port **8180** so it does not collide with the gateway (8080 / 8088), `httpd` on 8080, or WireMock 8081/8082.

Import a realm file from the repo, for example `local/keycloak/banking-realm.json`.

Realm sketch:

| Item | Value |
| --- | --- |
| Realm | `banking` |
| Issuer | `http://127.0.0.1:8180/realms/banking` |
| Audience | `banking-gateway` (configure as a client scope / audience mapper so access tokens actually carry it) |
| Access token lifespan | short, e.g. 5 minutes |

Clients (public client secrets in this file are **local demo only**, never production values):

| Client id | Grant | Scopes to request |
| --- | --- | --- |
| `customer-app` | authorization code or resource-owner/password **only if** you need a clickable local demo; client credentials plus a user token is enough if you document how to get one | `ledger.accounts.read`, `ledger.transfers.read`, `ledger.transfers.write`, `transactions.read`, `customers.read`, `customers.write` |
| `operations-app` | same | `operations.read`, `operations.admin`, plus any read scopes ops actually need |
| `service-app` | **client credentials only** | a tight subset, e.g. `transactions.read`, `ledger.accounts.read` — not `operations.admin` |

Export the realm from Keycloak after you have it working, sanitize it (no real passwords, no external URLs), and commit that. Operators recreate the demo with `docker compose up`.

Admin bootstrap password belongs in `.env` / compose env, listed in `.env.example`, never hardcoded as a production secret.

## 10. Suggested shape (you write the files)

Keep packages small. Something like:

```text
security/
  GatewaySecurityProperties.java      # issuer + audience, validated
  SecurityConfiguration.java          # SecurityWebFilterChain
  JwtAudienceValidator.java           # OAuth2TokenValidator<Jwt>
  RouteScopeAuthorizationManager.java # or equivalent filter; the table in §7
  BearerTokenQueryRejector.java       # WebFilter, high precedence
```

Extend, do not replace:

- `GatewayErrorCode` / mapper / handler for 401 and 403
- `AccessLogWebFilter` with `clientId` / `subject` when a JWT is present
- `docker-compose.yml` with Keycloak
- `.env.example` with the two `GATEWAY_SECURITY_*` variables
- `docs/local-setup.md` with “get a token, call a protected route, fail with the wrong scope”

Do not create empty packages for Phase 4+.

## 11. Tests (this is the exit criterion)

Unit:

- audience validator: wrong `aud` fails, matching `aud` passes, multi-aud containing ours passes
- scope table: each row in §7, plus a negative (GET transfers with only `ledger.accounts.read`)
- query-token rejector
- JWT decoder / algorithm allow-list if you wrap it

Gateway integration (WireMock stays; **sign JWTs in-process** with an RSA key the test decoder trusts — do not require Docker Keycloak for `./mvnw test`):

| Case | Expected |
| --- | --- |
| no `Authorization` on `/api/ledger/accounts/acc-1` | 401 problem, `invalid-credentials` |
| token in `?access_token=` only | 401, request never reaches WireMock |
| header + query token | 401, never reaches WireMock |
| expired / wrong issuer / wrong audience / `alg` none or HS256 | 401 |
| valid customer token, `GET /api/ledger/accounts/acc-1` with `ledger.accounts.read` | 200, WireMock hit |
| same token `POST /api/ledger/transfers` without `ledger.transfers.write` | 403 `insufficient-scope`, WireMock **not** hit |
| operations token `POST /api/operations/status` with `operations.admin` | 200 |
| customer token on `/api/operations/**` | 403 |
| valid token, unknown path | 404 problem from Phase 2 |
| `/actuator/health` without token | 200 |
| 401/403 body | problem+json, `correlationId`, no `exception`, no `trace`, no token text |

Keep the Phase 1/2 routing tests. They now need a valid token (or you mark actuator-only tests as public). A helper that mints a signed JWT with chosen scopes will save you pain.

## 12. Local demo script (for `docs/local-setup.md`)

After Keycloak is up:

1. Obtain a customer token (document the exact `curl` against the token endpoint).
2. `GET /api/ledger/accounts/acc-demo` with `Authorization: Bearer` → 200.
3. Same call without the header → 401.
4. Token with only `transactions.read` against `POST /api/ledger/transfers` → 403.
5. Health without a token → 200.

## 13. Done when

- [ ] Issuer and audience required at boot; `local` profile points at Keycloak
- [ ] JWKS validation, not introspection
- [ ] RS256 (or documented allow-list); `none` and HMAC rejected
- [ ] Query-string tokens rejected
- [ ] Scope table in §7 enforced; default deny
- [ ] 401 / 403 are Phase 2 problem details
- [ ] Health probes remain unauthenticated
- [ ] Access logs can show `client_id` / `sub`, never the token
- [ ] Realm file is sanitized and compose-imported
- [ ] `./mvnw test` covers the table in §11 without a live Keycloak
- [ ] You can demonstrate the four local-demo steps

## 14. Out of scope (do not sneak in)

Partner API keys, mTLS, opaque-token introspection, Redis rate limits, timeouts, circuit breakers. Those are later phases.

When the code is ready, ask for a review against this file and the §11 table.
