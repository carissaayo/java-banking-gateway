# Local run — Phases 1–2

From `banking-api-gateway/`:

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows PowerShell, `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.

Without the `local` profile you must set `GATEWAY_UPSTREAMS_LEDGER` and `GATEWAY_UPSTREAMS_CUSTOMER` (see `.env.example`). Missing URIs fail startup on purpose.

Exercise routing and correlation:

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/api/ledger/accounts/acc-demo
curl -i http://127.0.0.1:8080/api/ledger/accounts/acc-demo -H "X-Correlation-ID: corr-1234"
curl -i http://127.0.0.1:8080/api/ledger/transfers -H "Content-Type: application/json" -H "Idempotency-Key: demo-1" -d "{}"
curl -i http://127.0.0.1:8080/api/transactions/txn-demo
curl -i http://127.0.0.1:8080/api/customers/cus-demo
curl -i http://127.0.0.1:8080/api/operations/status
curl -i http://127.0.0.1:8080/api/does-not-exist
```

Successful proxied responses include `X-Gateway-Route` and `X-Correlation-ID`. Unknown paths return RFC 9457 problem JSON with a correlation id and no stack trace.

Verify:

```bash
./mvnw verify
```
