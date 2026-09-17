# Local run — Phase 1

From `banking-api-gateway/`:

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows PowerShell, `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.

Without the `local` profile you must set `GATEWAY_UPSTREAMS_LEDGER` and `GATEWAY_UPSTREAMS_CUSTOMER` (see `.env.example`). Missing URIs fail startup on purpose.

Exercise routing:

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/api/ledger/accounts/acc-demo
curl -i http://127.0.0.1:8080/api/ledger/transfers -H "Content-Type: application/json" -H "Idempotency-Key: demo-1" -d "{}"
curl -i http://127.0.0.1:8080/api/transactions/txn-demo
curl -i http://127.0.0.1:8080/api/customers/cus-demo
curl -i http://127.0.0.1:8080/api/operations/status
curl -i http://127.0.0.1:8080/api/does-not-exist
```

Successful proxied responses include `X-Gateway-Route`. Unknown paths return 404.

Verify:

```bash
./mvnw verify
```
