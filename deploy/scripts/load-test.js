// ============================================================================
// Teste de carga (k6) — gera tráfego para observar o HPA escalar e os painéis
// do Grafana reagirem. Rodar com Docker (não precisa instalar o k6):
//
//   PowerShell: Get-Content deploy\scripts\load-test.js | docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run -
//   bash/WSL  : docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < deploy/scripts/load-test.js
//
// Cada usuário virtual: cadastra-se, faz login, cria transações, lista e pede relatórios.
// ============================================================================
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const JSON_HEADERS = { "Content-Type": "application/json" };

export const options = {
  stages: [
    { duration: "30s", target: 10 },  // aquecimento
    { duration: "2m", target: 40 },   // carga sustentada (HPA deve reagir)
    { duration: "30s", target: 0 },   // desaceleração
  ],
  thresholds: {
    http_req_failed: ["rate<0.05"],          // menos de 5% de erros
    http_req_duration: ["p(95)<2000"],       // 95% abaixo de 2 s
  },
};

export function setup() {
  const email = `carga-${Date.now()}@finora.com`;
  const password = "Senha123";
  http.post(`${BASE_URL}/auth/register`, JSON.stringify({ name: "Carga", email, password }), { headers: JSON_HEADERS });
  const login = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email, password }), { headers: JSON_HEADERS });
  return { token: login.json("accessToken") };
}

export default function (data) {
  const headers = { ...JSON_HEADERS, Authorization: `Bearer ${data.token}` };

  const created = http.post(`${BASE_URL}/transactions`, JSON.stringify({
    description: "Compra de teste", amount: Math.round(Math.random() * 500) + 1,
    type: Math.random() < 0.3 ? "INCOME" : "EXPENSE", category: "Carga", date: "2026-09-15",
  }), { headers });
  check(created, { "transação criada (201)": (r) => r.status === 201 });

  const list = http.get(`${BASE_URL}/transactions`, { headers });
  check(list, { "listagem (200)": (r) => r.status === 200 });

  if (Math.random() < 0.2) {
    const report = http.post(`${BASE_URL}/reports`, "{}", { headers });
    check(report, { "relatório aceito (202)": (r) => r.status === 202 });
  }
  sleep(0.5);
}
