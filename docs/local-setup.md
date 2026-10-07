# Local run — Phases 1–3

From `banking-api-gateway/`:

```bash
copy .env.example .env
docker compose up -d
```

On Git Bash: `cp .env.example .env`.

Wait until Keycloak answers (first start can take a minute):

```bash
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8180/realms/banking
```

Expect `200`. Admin console: `http://127.0.0.1:8180` (user `admin`, password from `.env`).

Start the gateway with the `local` profile (issuer `http://127.0.0.1:8180/realms/banking`, audience `banking-gateway`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.

If the gateway port 8080 is busy, use `"-Dspring-boot.run.arguments=--server.port=8088"` and replace `8080` below.

Without the `local` profile you must set `GATEWAY_UPSTREAMS_*` and `GATEWAY_SECURITY_*` (see `.env.example`). Missing values fail startup on purpose.

## Get a token and call a route

Client secrets in `local/keycloak/banking-realm.json` are **local demo only**.

Customer token with account read:

```bash
curl.exe -s -X POST "http://127.0.0.1:8180/realms/banking/protocol/openid-connect/token" ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "grant_type=client_credentials" ^
  -d "client_id=customer-app" ^
  -d "client_secret=local-demo-customer-secret" ^
  -d "scope=ledger.accounts.read"
```

Git Bash / bash (copy `access_token` from the JSON, or):

```bash
TOKEN=$(curl.exe -s -X POST "http://127.0.0.1:8180/realms/banking/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=customer-app&client_secret=local-demo-customer-secret&scope=ledger.accounts.read" \
  | jq -r .access_token)
```

1. Valid token, account read → 200:

```bash
curl.exe -i http://127.0.0.1:8080/api/ledger/accounts/acc-demo -H "Authorization: Bearer $TOKEN"
```

2. Same path, no header → 401 `invalid-credentials`.

```bash
curl.exe -i http://127.0.0.1:8080/api/ledger/accounts/acc-demo
```

3. Service token (`transactions.read` + `ledger.accounts.read` only) on a transfer write → 403 `insufficient-scope`:

```bash
SERVICE=$(curl.exe -s -X POST "http://127.0.0.1:8180/realms/banking/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=service-app&client_secret=local-demo-service-secret" \
  | jq -r .access_token)

curl.exe -i -X POST http://127.0.0.1:8080/api/ledger/transfers \
  -H "Authorization: Bearer $SERVICE" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d "{}"
```

4. Health stays public:

```bash
curl.exe -i http://127.0.0.1:8080/actuator/health
```

Operations write uses `operations-app` and `scope=operations.admin`.

Do not put tokens on the query string (`?access_token=`). The gateway returns 401.

## Automated tests

```bash
./mvnw test
```

That suite signs JWTs in-process. It does **not** start Keycloak.

## Clients (local demo)

| Client | Secret | Typical scopes |
| --- | --- | --- |
| `customer-app` | `local-demo-customer-secret` | request the ones you need, e.g. `ledger.accounts.read` |
| `operations-app` | `local-demo-operations-secret` | `operations.read`, `operations.admin` |
| `service-app` | `local-demo-service-secret` | default `ledger.accounts.read` + `transactions.read` |
