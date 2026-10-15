# Local run

From `banking-api-gateway/`:

```bash
cp .env.example .env
docker compose up -d --build
```

Windows cmd: `copy .env.example .env`.

`.env` must set `KEYCLOAK_ADMIN_PASSWORD`. Compose does not invent one.

Wait until Keycloak answers (first start can take a minute), then until gateway health is 200:

```bash
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8180/realms/banking
curl.exe -s -o NUL -w "%{http_code}" http://localhost:8080/actuator/health
```

Compose publishes the gateway on **8080**. If `127.0.0.1:8080` is already another HTTP server, use `http://localhost:8080` or `GATEWAY=...`. Do not change the published ports.

Admin console: `http://127.0.0.1:8180` (user `admin`, password from `.env`).

Ports stay **8080** gateway, **8081** ledger mock, **8082** customer mock, **8180** Keycloak, **6379** Redis.

## Scripted walkthrough

```bash
bash scripts/demo.sh
```

That is the 12-step README demo (health, JWT, 401/403, 429, timeout + GET retry, no POST retry, circuit, correlation, Prometheus). Editor collection: `http/gateway-demo.http`.

## Gateway on the host (optional)

If you would rather run the JVM on the host and only compose the dependencies:

```bash
docker compose up -d mock-ledger mock-customer keycloak redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.

If 8080 is busy (Apache), use `"-Dspring-boot.run.arguments=--server.port=8088"` and replace `8080` below. Prefer freeing 8080 for the demo.

Do **not** set `GATEWAY_SECURITY_JWK_SET_URI` on the host. That variable is for the **gateway container** (JWKS over the Compose network while `iss` stays `http://127.0.0.1:8180/realms/banking`).

Without the `local` profile you must set `GATEWAY_UPSTREAMS_*`, `GATEWAY_SECURITY_*`, and `GATEWAY_REDIS_URI` (see `.env.example`). Missing values fail startup on purpose.

## Get a token and call a route

Client secrets in `local/keycloak/banking-realm.json` are **local demo only**.

```bash
TOKEN=$(curl.exe -s -X POST "http://127.0.0.1:8180/realms/banking/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=customer-app&client_secret=local-demo-customer-secret&scope=ledger.accounts.read" \
  | jq -r .access_token)

curl.exe -i http://localhost:8080/api/ledger/accounts/acc-demo -H "Authorization: Bearer $TOKEN"
```

No header → 401. `service-app` on `POST /api/ledger/transfers` → 403. Health stays public:

```bash
curl.exe -i http://localhost:8080/actuator/health
```

Do not put tokens on the query string (`?access_token=`). The gateway returns 401.

## Automated tests

```bash
./mvnw verify
```

That suite signs JWTs in-process. It does **not** start Keycloak. CI runs the same command, then `docker build`.

Optional format (does not rewrite the tree on `verify`):

```bash
./mvnw spotless:apply
```

## Clients (local demo)

| Client | Secret | Typical scopes |
| --- | --- | --- |
| `customer-app` | `local-demo-customer-secret` | request the ones you need, e.g. `ledger.accounts.read` |
| `operations-app` | `local-demo-operations-secret` | `operations.read`, `operations.admin` |
| `service-app` | `local-demo-service-secret` | default `ledger.accounts.read` + `transactions.read` |
| `other-api-app` | `local-demo-other-secret` | same read scope, audience `other-api` (expect 401) |
