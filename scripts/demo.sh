#!/usr/bin/env bash
# Local demo for the 12 README steps. Run from banking-api-gateway/ after:
#   cp .env.example .env
#   docker compose up -d --build
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
KEYCLOAK="${KEYCLOAK:-http://127.0.0.1:8180}"
LEDGER_ADMIN="${LEDGER_ADMIN:-http://127.0.0.1:8081/__admin}"
WAIT_BODY="/tmp/gateway-demo-wait"
WAIT_HDR="/tmp/gateway-demo-wait-h"
if command -v curl.exe >/dev/null 2>&1; then
	CURL=(curl.exe)
else
	CURL=(curl)
fi

token() {
	local client="$1"
	local secret="$2"
	local scope="${3:-}"
	local data="grant_type=client_credentials&client_id=${client}&client_secret=${secret}"
	if [ -n "$scope" ]; then
		data="${data}&scope=${scope}"
	fi
	local body
	body="$("${CURL[@]}" -sS -X POST "${KEYCLOAK}/realms/banking/protocol/openid-connect/token" \
		-H "Content-Type: application/x-www-form-urlencoded" \
		--data "$data")"
	local access
	access="$(printf '%s' "$body" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
	if [ -z "$access" ]; then
		echo "failed to get token for ${client}: ${body}" >&2
		exit 1
	fi
	printf '%s' "$access"
}

http_code() {
	"${CURL[@]}" -sS -o /tmp/gateway-demo-body -w "%{http_code}" "$@"
}

wait_for() {
	local url="$1"
	local name="$2"
	local i code
	for i in $(seq 1 60); do
		code="$("${CURL[@]}" -sS -L -D "$WAIT_HDR" -o "$WAIT_BODY" -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || true)"
		if [ "$code" = "200" ]; then
			echo "ready: ${name}"
			return 0
		fi
		if [ "$name" = "gateway" ] && grep -qi "^Server: Apache" "$WAIT_HDR" 2>/dev/null; then
			echo "${url} is another HTTP server, not the gateway. Set GATEWAY to the compose mapping (port 8080) or free that port." >&2
			exit 1
		fi
		sleep 2
	done
	echo "timed out waiting for ${name} at ${url} (last HTTP ${code:-none})" >&2
	exit 1
}

echo "== wait for Keycloak, mocks, and gateway =="
wait_for "${KEYCLOAK}/realms/banking" "keycloak"
wait_for "http://127.0.0.1:8081/__admin" "mock-ledger"
wait_for "http://127.0.0.1:8082/__admin" "mock-customer"
wait_for "${GATEWAY}/actuator/health" "gateway"

CUSTOMER="$(token customer-app local-demo-customer-secret "ledger.accounts.read ledger.transfers.write customers.read")"
OPERATIONS="$(token operations-app local-demo-operations-secret "operations.read")"
SERVICE="$(token service-app local-demo-service-secret "")"
WRONG_AUD="$(token other-api-app local-demo-other-secret "ledger.accounts.read")"

echo
echo "== 3. public health =="
code="$(http_code "${GATEWAY}/actuator/health")"
echo "health ${code}"
test "$code" = "200"

echo
echo "== 4. protected account read =="
code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-demo" -H "Authorization: Bearer ${CUSTOMER}")"
echo "account ${code}"
test "$code" = "200"

echo
echo "== 5. missing token, wrong scope, wrong audience =="
code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-demo")"
echo "no token ${code}"
test "$code" = "401"
code="$(http_code -X POST "${GATEWAY}/api/ledger/transfers" \
	-H "Authorization: Bearer ${SERVICE}" \
	-H "Content-Type: application/json" \
	-H "Idempotency-Key: demo-scope-1" \
	-d "{}")"
echo "wrong scope ${code}"
test "$code" = "403"
code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-demo" -H "Authorization: Bearer ${WRONG_AUD}")"
echo "wrong audience ${code}"
test "$code" = "401"

echo
echo "== 6. transfer burst -> 429 =="
limited=0
i=0
while [ "$i" -lt 8 ]; do
	i=$((i + 1))
	code="$(http_code -X POST "${GATEWAY}/api/ledger/transfers" \
		-H "Authorization: Bearer ${CUSTOMER}" \
		-H "Content-Type: application/json" \
		-H "Idempotency-Key: demo-burst-${i}" \
		-d "{}")"
	echo "transfer ${i} ${code}"
	if [ "$code" = "429" ]; then
		limited=$((limited + 1))
	fi
done
test "$limited" -ge 1

echo
echo "== 7. slow GET -> 504 =="
"${CURL[@]}" -sS -X DELETE "${LEDGER_ADMIN}/requests" >/dev/null
code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-slow" -H "Authorization: Bearer ${CUSTOMER}")"
echo "slow get ${code}"
test "$code" = "504"
hits="$("${CURL[@]}" -sS -X POST "${LEDGER_ADMIN}/requests/count" \
	-H "Content-Type: application/json" \
	-d '{"method":"GET","urlPath":"/accounts/acc-slow"}')"
echo "slow get upstream hits ${hits}"
printf '%s' "$hits" | grep -Eq '"count"[[:space:]]*:[[:space:]]*[1-9]'

echo
echo "== 10a. slow POST is not retried =="
"${CURL[@]}" -sS -X POST "${LEDGER_ADMIN}/mappings" \
	-H "Content-Type: application/json" \
	-d '{"priority":1,"request":{"method":"POST","urlPath":"/transfers"},"response":{"status":201,"fixedDelayMilliseconds":5000,"headers":{"Content-Type":"application/json"},"jsonBody":{"id":"slow"}}}' \
	>/dev/null
"${CURL[@]}" -sS -X DELETE "${LEDGER_ADMIN}/requests" >/dev/null
code="$(http_code -X POST "${GATEWAY}/api/ledger/transfers" \
	-H "Authorization: Bearer ${CUSTOMER}" \
	-H "Content-Type: application/json" \
	-H "Idempotency-Key: demo-slow-write" \
	-d "{}")"
echo "slow post ${code}"
test "$code" = "504"
hits="$("${CURL[@]}" -sS -X POST "${LEDGER_ADMIN}/requests/count" \
	-H "Content-Type: application/json" \
	-d '{"method":"POST","urlPath":"/transfers"}')"
echo "slow post upstream hits ${hits}"
printf '%s' "$hits" | grep -Eq '"count"[[:space:]]*:[[:space:]]*1'
"${CURL[@]}" -sS -X POST "${LEDGER_ADMIN}/mappings/reset" >/dev/null

echo
echo "== 8. circuit opens after repeated 500s =="
i=0
while [ "$i" -lt 8 ]; do
	i=$((i + 1))
	code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-down" -H "Authorization: Bearer ${CUSTOMER}")"
	echo "down ${i} ${code}"
done
code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-down" -H "Authorization: Bearer ${CUSTOMER}")"
echo "circuit ${code}"
test "$code" = "503"

echo
echo "== 9. recover (wait 11s for half-open) =="
"${CURL[@]}" -sS -X POST "${LEDGER_ADMIN}/mappings" \
	-H "Content-Type: application/json" \
	-d '{"priority":1,"request":{"method":"GET","urlPath":"/accounts/acc-down"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"id":"acc-down"}}}' \
	>/dev/null
sleep 11
code="$(http_code "${GATEWAY}/api/ledger/accounts/acc-down" -H "Authorization: Bearer ${CUSTOMER}")"
echo "recovered ${code}"
test "$code" = "200"
"${CURL[@]}" -sS -X POST "${LEDGER_ADMIN}/mappings/reset" >/dev/null

echo
echo "== 11. correlation id =="
"${CURL[@]}" -sS -D /tmp/gateway-demo-headers -o /tmp/gateway-demo-body \
	"${GATEWAY}/api/ledger/accounts/acc-demo" \
	-H "Authorization: Bearer ${CUSTOMER}" \
	-H "X-Correlation-ID: demo-corr-1" >/dev/null
grep -i "^x-correlation-id: demo-corr-1" /tmp/gateway-demo-headers

echo
echo "== 12. prometheus (JWT required) =="
code="$(http_code "${GATEWAY}/actuator/prometheus")"
echo "prometheus anonymous ${code}"
test "$code" = "401"
code="$(http_code "${GATEWAY}/actuator/prometheus" -H "Authorization: Bearer ${OPERATIONS}")"
echo "prometheus auth ${code}"
test "$code" = "200"
grep -q "jvm_memory_used_bytes" /tmp/gateway-demo-body

echo
echo "demo ok"
