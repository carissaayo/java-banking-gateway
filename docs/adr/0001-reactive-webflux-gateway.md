# ADR 0001: Reactive Spring Cloud Gateway

## Status

Accepted for this project.

## Context

Spring Boot 4 / Spring Cloud 2025.1 splits the gateway into two starters:

- `spring-cloud-starter-gateway-server-webflux` (Netty, non-blocking)
- `spring-cloud-starter-gateway-server-webmvc` (servlet, blocking)

Spring Initializr currently maps the generic `cloud-gateway` checkbox to the servlet starter. The project README requires a reactive gateway.

## Decision

Use the WebFlux gateway on Java 21 with the Oakwood release train (Spring Cloud `2025.1.3` on Spring Boot `4.1.1`).

Do not add Spring MVC or blocking persistence to the request path.

## Consequences

- Routes use `RouteLocator` / YAML under `spring.cloud.gateway.server.webflux`, not the old `spring.cloud.gateway.routes` key.
- Filters must stay reactive (`Mono`/`Flux`). A blocking Redis/JDBC call on the event loop would stall unrelated requests.
- Debugging a reactor chain is harder than a servlet stack. That is accepted because the portfolio goal is reactive edge behavior: high connection counts, streaming, and non-blocking rate limiting later.

## Alternatives considered

- **Servlet gateway.** Simpler mental model, worse fit for the stated learning goals and for Redis rate limiting that already has a reactive Gateway filter.
- **Envoy/nginx only.** Excellent production proxies, but they do not demonstrate Java, Spring Security, or testable Java filters.
