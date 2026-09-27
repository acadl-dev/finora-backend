# Finora — Back-end (microsserviços)

| Serviço | Porta | Papel |
|---|---|---|
| `server-service` | 8761 | **Service Discovery** (Spring Cloud Netflix Eureka Server). Painel: http://localhost:8761 |
| `gateway-service` | 8080 | **API Gateway** (Spring Cloud Gateway). Porta única usada pelo front-end |
| `finora` | 8082 | Contexto de **Identidade e Transações** (login, receitas e despesas) — Postgres `finora` (5433) |
| `reports-service` | 8083 | Contexto de **Relatórios** (Excel + histórico) — Postgres **próprio** `finora_reports` (5434) |

```
Front (Next.js) ──► gateway-service :8080 ──┬─► /auth/**, /transactions/** ─► finora
                            │               └─► /reports/**                 ─► reports-service
                            ▼                                                     │ OpenFeign (lb://finora)
                     server-service (Eureka) ◄── todos se registram ──────────────┘
```

## Como executar (nesta ordem)

1. Bancos de dados (Docker):
   ```bash
   cd finora && docker compose up -d
   cd ../reports-service && docker compose up -d
   ```
2. `server-service` (Eureka) → `./mvnw spring-boot:run`
3. `finora` → `./mvnw spring-boot:run`
4. `reports-service` → `./mvnw spring-boot:run` (usa o mesmo `JWT_SECRET` do finora, no arquivo `.env`)
5. `gateway-service` → `./mvnw spring-boot:run`
6. Front-end: `front_finora/finora` → `npm run dev` (`.env.local` aponta `API_BASE_URL` para o gateway, `http://localhost:8080`)

Confira em http://localhost:8761 se `FINORA`, `REPORTS-SERVICE` e `GATEWAY-SERVICE` aparecem como UP
(o registro leva ~30 s após subir cada serviço).

## Endpoints novos (via gateway)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/reports/transactions/excel?start=yyyy-MM-dd&end=yyyy-MM-dd` | Gera e baixa o Excel de receitas/despesas com o saldo. Datas opcionais. |
| GET | `/reports/history` | Últimos 20 relatórios gerados pelo usuário (período, totais, saldo). |
| GET | `/transactions` | Lista as transações do usuário (consumido pelo reports-service via Feign). |

Todos exigem `Authorization: Bearer <token>`.
