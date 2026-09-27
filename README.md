# Finora — Back-end (microsserviços orientados a eventos)

| Serviço | Porta | Papel |
|---|---|---|
| `server-service` | 8761 | **Service Discovery** (Spring Cloud Netflix Eureka Server). Painel: http://localhost:8761 |
| `gateway-service` | 8080 | **API Gateway** (Spring Cloud Gateway). Porta única usada pelo front-end |
| `finora` | 8082 | Contexto de **Identidade e Transações**. Postgres `finora` (5433). **Produtor** de eventos (Transactional Outbox) |
| `reports-service` | 8083 | Contexto de **Relatórios**. Postgres **próprio** `finora_reports` (5434). **Consumidor** de eventos + worker de relatórios |
| RabbitMQ | 5672 / 15672 | **Message broker**. Painel: http://localhost:15672 (usuário `finora`, senha `finora`) |

## Arquitetura

```
                         ┌──────────────── server-service (Eureka) ◄── todos se registram
                         │
Front (Next.js) ──► gateway-service :8080 ──┬─► /auth/**, /transactions/** ─► finora ──┐
                                            └─► /reports/**                 ─► reports-service
                                                                                 ▲      │
   finora: salva transação + evento na outbox (mesma transação do banco)        │      │
   OutboxRelay ──TransactionRegistered / TransactionRemoved──► RabbitMQ ─────────┘      │
                         exchange finora.transactions (topic)                           │
                                                                                        │
   POST /reports ──GenerateReportCommand──► RabbitMQ (finora.reports.commands) ──► worker (2 a 4 consumidores)
```

Não há mais chamada HTTP entre microsserviços: o reports-service mantém uma **projeção** das transações
(tabela `ledger_entries`) alimentada pelos eventos e gera os relatórios a partir dela.

### Topologia RabbitMQ

| Exchange (tipo) | Routing key | Fila | Consumidor | Padrão |
|---|---|---|---|---|
| `finora.transactions` (topic) | `transaction.registered`, `transaction.removed` → binding `transaction.#` | `reports.transaction-events` | `TransactionEventsListener` (1 consumidor, preserva ordem) | Evento (publish/subscribe) |
| `finora.reports.commands` (direct) | `report.generate` | `reports.generate-report` | `GenerateReportCommandListener` (2 a 4 consumidores) | Comando (fila de trabalho) |
| `finora.dlx` (direct) | nome da DLQ | `reports.transaction-events.dlq`, `reports.generate-report.dlq` | DLQ de relatórios marca o relatório como FAILED | Dead Letter Channel |

Falha no consumo → 3 tentativas com backoff exponencial (1 s, 2 s, 4 s) → DLQ.

## Como executar (nesta ordem)

1. Infraestrutura (Docker):
   ```bash
   docker compose up -d                 # RabbitMQ (nesta pasta, back_finora)
   cd finora && docker compose up -d    # Postgres do finora (5433)
   cd ../reports-service && docker compose up -d   # Postgres do reports-service (5434)
   ```
2. `server-service` (Eureka) → `./mvnw spring-boot:run`
3. `reports-service` → `./mvnw spring-boot:run` (declara filas e bindings no RabbitMQ)
4. `finora` → `./mvnw spring-boot:run` (publica os eventos pendentes da outbox, inclusive das transações antigas)
5. `gateway-service` → `./mvnw spring-boot:run`
6. Front-end: `front_finora/finora` → `npm run dev` (`API_BASE_URL=http://localhost:8080`, o gateway)

> A ordem 3 → 4 é a recomendada, mas não obrigatória: se o finora subir antes, os eventos ficam na
> outbox (mensagem sem fila de destino não é marcada como publicada) e são enviados assim que o reports-service criar a fila.

Confira:
- http://localhost:8761 — `FINORA`, `REPORTS-SERVICE` e `GATEWAY-SERVICE` como UP (~30 s).
- http://localhost:15672 → *Queues* — `reports.transaction-events`, `reports.generate-report` e as DLQs.

## Endpoints (via gateway)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/transactions` | Cria receita/despesa. Grava a transação e o evento `TransactionRegistered` na outbox |
| GET | `/transactions` | Lista as transações do usuário |
| DELETE | `/transactions/{id}` | Exclui a transação (só o dono). Grava `TransactionRemoved` na outbox |
| POST | `/reports` | Body `{ "start": "yyyy-MM-dd", "end": "yyyy-MM-dd" }` (opcionais). **202 Accepted**: relatório `REQUESTED` |
| GET | `/reports/{id}` | Status do relatório: `REQUESTED`, `READY` ou `FAILED` |
| GET | `/reports/{id}/file` | Baixa o Excel (409 se ainda não está pronto) |
| GET | `/reports/history` | Últimos 20 relatórios do usuário, com status e saldo |

Todos exigem `Authorization: Bearer <token>`.

## Mudanças de banco

- `finora`: nova tabela `outbox_events` (criada pelo Hibernate, `ddl-auto: update`).
- `reports-service`: novas tabelas `ledger_entries`, `processed_events`, `reports` e `report_contents`.
  A tabela antiga `financial_reports` (etapa anterior) não é mais usada e pode ser apagada.
