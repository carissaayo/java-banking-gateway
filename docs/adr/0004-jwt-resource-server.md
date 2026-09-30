# ADR 0004: JWT resource server at the edge

## Status

Proposed for Phase 3. Accept when you implement it.

## Context

Phase 0 requires issuer, audience, algorithm, expiry, and per-route scopes. The gateway is reactive. Tokens must be validated without a network call to Keycloak on every request.

The current `ledger-write` RouteLocator entry is coarser than the scope table (read vs write, accounts vs transfers).

## Decision

- Use Spring Security WebFlux as an OAuth2 resource server.
- Validate JWTs from the issuer JWKS. Require `gateway.security.issuer-uri` and `gateway.security.audience` at boot.
- Allow RS256. Reject `none` and HMAC.
- Enforce the method+path scope table in `docs/phase-3-oauth2-jwt.md` §7, not one scope per RouteLocator id.
- Reject tokens in query parameters even if a valid Bearer header is also present.
- Leave `/actuator/health` (`liveness` / `readiness`) public. Authenticate `/api/**`.
- Map 401 / 403 to the Phase 2 problem-details types `invalid-credentials` and `insufficient-scope`.
- Sign test JWTs in-process. Keycloak is for local demo via Compose, not a testcontainers requirement for this phase unless tests cannot otherwise cover JWKS fetch.

## Consequences

- Anonymous unknown paths become 401 instead of 404. That is acceptably more opaque.
- Downstream services still authorize the resource. This ADR does not change that.
- Splitting RouteLocator vs a custom authorization manager is an implementation detail; the scope table is not.
