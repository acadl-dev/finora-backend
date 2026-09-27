#!/usr/bin/env bash
# ============================================================================
# Smoke test ponta a ponta (sistema COMPLETO em execução, via gateway).
# Valida os microsserviços funcionando em conjunto:
#   gateway -> finora (cadastro/login/transações) -> outbox -> RabbitMQ
#   -> reports-service (projeção) -> fila de relatórios -> worker -> Excel
#
# Uso: BASE_URL=http://localhost:8080 ./deploy/scripts/smoke-test.sh
# Usado no pipeline de CD (após o deploy no Kubernetes) e na apresentação.
# ============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="smoke-$(date +%s)-$RANDOM@finora.com"
PASSWORD="Senha123"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass() { echo -e "  \033[32m✔\033[0m $1"; }
fail() { echo -e "  \033[31m✘ $1\033[0m"; exit 1; }
json() { python3 -c "import sys, json; print(json.load(sys.stdin)$1)"; }

# Chama a API e guarda status + corpo (com novas tentativas enquanto o Eureka propaga)
call() {
  local method=$1 path=$2 body=${3:-} token=${4:-}
  local args=(-s -o "$TMP/body" -w "%{http_code}" -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}"
}

echo "Smoke test em $BASE_URL"

echo "1) Gateway e serviços registrados"
for i in $(seq 1 30); do
  code=$(call POST /auth/register "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" || true)
  [ "$code" = "200" ] && break
  echo "   aguardando o gateway rotear para o finora (HTTP $code)..."; sleep 5
done
[ "$code" = "200" ] && pass "cadastro de usuário (finora via gateway)" || fail "cadastro falhou (HTTP $code)"

echo "2) Autenticação JWT"
code=$(call POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
[ "$code" = "200" ] || fail "login falhou (HTTP $code)"
TOKEN=$(json "['accessToken']" < "$TMP/body")
pass "login e token JWT"

echo "3) Receita e despesa"
code=$(call POST /transactions '{"description":"Salário","amount":5000,"type":"INCOME","category":"Salário","date":"2026-09-01"}' "$TOKEN")
[ "$code" = "201" ] || fail "receita não criada (HTTP $code)"
code=$(call POST /transactions '{"description":"Aluguel","amount":1800,"type":"EXPENSE","category":"Moradia","date":"2026-09-05"}' "$TOKEN")
[ "$code" = "201" ] || fail "despesa não criada (HTTP $code)"
pass "transações criadas (eventos gravados na outbox)"

code=$(call GET /transactions "" "$TOKEN")
count=$(json ".__len__()" < "$TMP/body")
[ "$code" = "200" ] && [ "$count" = "2" ] && pass "listagem retorna 2 transações" || fail "listagem inesperada ($code, $count)"

echo "4) Relatório assíncrono (eventos + fila de comandos)"
sleep 3   # consistência eventual: tempo para os eventos chegarem à projeção do reports-service
code=$(call POST /reports '{}' "$TOKEN")
[ "$code" = "202" ] || fail "pedido de relatório não aceito (HTTP $code)"
REPORT_ID=$(json "['id']" < "$TMP/body")
pass "pedido aceito (202) — relatório $REPORT_ID"

status=""
for i in $(seq 1 30); do
  call GET "/reports/$REPORT_ID" "" "$TOKEN" > /dev/null
  status=$(json "['status']" < "$TMP/body")
  [ "$status" != "REQUESTED" ] && break
  sleep 2
done
[ "$status" = "READY" ] || fail "relatório terminou como $status"
balance=$(json "['balance']" < "$TMP/body")
python3 -c "import sys; sys.exit(0 if abs(float('$balance') - 3200.0) < 0.01 else 1)" \
  && pass "relatório READY com saldo correto (R\$ $balance = 5000 - 1800)" \
  || fail "saldo incorreto: $balance (esperado 3200)"

code=$(curl -s -o "$TMP/report.xlsx" -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$BASE_URL/reports/$REPORT_ID/file")
[ "$code" = "200" ] && [ "$(head -c 2 "$TMP/report.xlsx")" = "PK" ] \
  && pass "download do Excel (.xlsx válido)" || fail "download do Excel falhou (HTTP $code)"

echo "5) Segurança"
code=$(call GET /reports/history)
[ "$code" = "401" ] && pass "sem token -> 401" || fail "esperado 401, veio $code"

echo -e "\n\033[32mSmoke test OK: os microsserviços funcionam em conjunto.\033[0m"
