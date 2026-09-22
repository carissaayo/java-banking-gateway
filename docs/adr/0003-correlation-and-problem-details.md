# ADR 0003: Correlation, tracing, and problem details

## Status

Accepted for Phase 2.

## Context

Every request that crosses this gateway needs a stable identifier for support, and clients must never see stack traces, upstream hostnames, or exception messages. Spring Cloud Gateway's default error body is not RFC 9457 and can leak `ConnectException` text.

## Decision

- Accept `X-Correlation-ID` only when it is 8–128 characters of `[A-Za-z0-9._-]`. Otherwise mint a UUID. Return it and forward it.
- Treat W3C `traceparent` as untrusted input. If valid, keep the trace id and create a new span id for this hop. Drop invalid `tracestate`.
- Apply this in a `WebFilter`, not only a Gateway `GlobalFilter`, so unmatched routes still get an id.
- Render gateway-owned failures as RFC 9457 `application/problem+json` with a `correlationId` member. Never copy `Throwable.getMessage()` into `detail`.
- Replace upstream 5xx bodies with the same envelope. Preserve the status code. Pass 4xx bodies through — those are often business errors.

## Consequences

- Support can join gateway access logs, upstream logs, and client reports on one id.
- A 404 now has a body. Clients that treated 404 as empty must read problem JSON.
- Preserving 4xx bodies means a buggy upstream can still put a stack trace in a 400. Phase 7 can tighten this if needed.
- Access logs use the path without the query string. Account ids in the path are still visible; they are not PANs, and templates can wait until we have a stable path vocabulary.
