# Phase 9 measurements

Taken on the author’s Windows machine (Java 21, Maven Surefire). These are **wall-clock IT numbers**, not production SLOs. Do not raise the ledger timeout to make them look prettier.

| Test | What it showed | Number |
| --- | --- | --- |
| `GatewayConcurrentReadIT` | 32 ledger GETs, concurrency 8, 50ms stub, warmup off the clock | `elapsedMs=396` vs sequential floor `1600` |
| `GatewayConcurrentMixedReadIT` | 16 ledger + 16 customer overlapping | `elapsedMs=536` vs `1600` |
| `GatewayConcurrentBurstIT` | 12 transfer POSTs, real Redis, burst 4 + replenish while in flight | `ok=6 limited=6` |
| `GatewayConcurrentDegradationIT` | Slow ledger 504s must not starve customer | 8×504 + 8×200, elapsed under 12s |
| `GatewayConcurrentUnavailableIT` | Dead ledger; test-profile fuse opens after 4 | mix of 503 unavailable / 503 circuit-open, 8×200 customer |
| `GatewayGracefulShutdownIT` | Drain then lock the door | access log upstream 200; client may see 504; `afterShutdown=-1` |

## Reading the numbers

- Overlap vs sequential is the WebFlux story. If elapsed is close to `N × delay`, the test is serial (usually warmup still on the clock, or `WebTestClient` in a loop).
- Burst is not exactly `burstCapacity`. Replenish keeps ticking during the in-flight window.
- Isolation is per circuit (`ledger` vs `customer`), not one global “bank is down”.
- Graceful shutdown is Netty stopping **new** connections. The test client can still see 504 while WireMock already returned 200.

p50/p95/p99 of **gateway-added** latency were not scraped from Prometheus in this phase. Histograms are enabled (`http.server.requests`, `spring.cloud.gateway.requests`); the Grafana dashboard in `docs/grafana/` is the place to read them once a reviewer is scraping `/actuator/prometheus`.
