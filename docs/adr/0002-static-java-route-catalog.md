# ADR 0002: Static Java route catalog

## Status

Accepted for Phase 1.

## Context

Spring Cloud Gateway can load routes from YAML or from a `RouteLocator` bean. Dynamic routes are an explicit later extension.

Upstream locations change per environment. Security-sensitive values must not silently default.

## Decision

- Define the MVP route catalog in Java (`StaticRouteCatalogConfiguration`) so predicates, methods, and rewrite rules are reviewed like code.
- Bind upstream URIs from configuration (`gateway.upstreams.*`). They have no default in `application.yml`. Set `GATEWAY_UPSTREAMS_LEDGER` and `GATEWAY_UPSTREAMS_CUSTOMER`, or use the `local` profile.
- Use two WireMock upstreams: ledger (accounts, transfers, transactions) and customer (customers, operations). That matches the Phase 1 exit criterion without pretending four independent services already exist.

## Consequences

- Changing a path or method requires a rebuild. Acceptable until Phase 1's static catalog is stable.
- Operators still change *where* traffic goes by environment variable, without editing Java.
- Operations and transaction-query share mocks with customer and ledger. Tests assert rewrite targets, so splitting those URIs later is a configuration change plus a new route URI, not a rewrite of the public API.

## Alternatives considered

- **YAML-only routes.** Easier for operators, weaker compile-time checking, easy to copy stale `spring.cloud.gateway.routes` from old tutorials.
- **Four upstreams immediately.** Honest topology later; extra compose services now without extra learning.
