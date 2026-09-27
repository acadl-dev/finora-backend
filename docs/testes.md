# Testes

A estratégia cobre cada componente **isoladamente** e o sistema **em conjunto**, em camadas
(pirâmide de testes). Todos rodam no CI a cada push.

| Camada | O que valida | Ferramentas | Onde |
|---|---|---|---|
| **Unitários de domínio** | Regras de negócio sem Spring/banco: agregados, Value Objects, eventos de domínio, serviço de domínio | JUnit 5, AssertJ | `finora/.../transaction/model/*Test`, `reports-service/.../report/model/FinancialReportTest`, `.../ledger/mapper/LedgerEntryMapperTest` |
| **Integração** | API REST + segurança JWT + PostgreSQL + RabbitMQ **reais** | Spring Boot Test, MockMvc, **Testcontainers**, Awaitility | `finora/.../TransactionFlowIT`, `reports-service/.../ReportFlowIT` |
| **Componente** | Gateway roteando para os serviços certos; Eureka respondendo | Spring Boot Test, WebTestClient, TestRestTemplate | `gateway-service/.../GatewayRoutesTest`, `server-service/.../ServerServiceApplicationTests` |
| **Front-end** | Validações de formulário, cliente da API (polling do relatório assíncrono), rota de health | Vitest | `front_finora/finora/src/**/*.test.ts` |
| **Infraestrutura** | Manifests Kubernetes e compose válidos | kubeconform, `docker compose config` | job *manifests* do CI |
| **Ponta a ponta (smoke)** | Sistema completo implantado: gateway → finora → outbox → RabbitMQ → reports-service → worker → Excel | bash + curl | `deploy/scripts/smoke-test.sh` (job *deploy* do CI) |
| **Carga** | Desempenho sob carga e HPA escalando | k6 | `deploy/scripts/load-test.js` |

## Casos cobertos

**finora**
- `Money`: arredondamento, rejeição de valores nulos/zero/negativos, igualdade por valor.
- `Transaction`: id gerado pelo domínio, normalização, sinal do valor, evento `TransactionRegistered`
  com todos os dados, eventos entregues uma única vez, só o dono exclui (`TransactionRemoved`).
- `TransactionFlowIT`: cadastro grava na outbox **e** publica no RabbitMQ (confirmado pelo broker,
  `messageId = eventId`); exclusão publica `TransactionRemoved`; usuário não exclui transação de
  outro (404); listagem isolada por usuário; sem token → 403; valor zero → 400 com mensagem de
  negócio; senha errada → 401; `/actuator/health`, readiness e métricas expostos.

**reports-service**
- `FinancialReportTest` / `LedgerEntryMapperTest`: saldo = receitas − despesas, período, ciclo de
  vida REQUESTED → READY/FAILED, tradução do evento (Anti-Corruption Layer), lápide.
- `ReportFlowIT`: evento alimenta a projeção; **evento duplicado é ignorado** (idempotência);
  exclusão vira lápide; exclusão que chega **antes** do registro não "ressuscita" a transação;
  **mensagem inválida vai para a DLQ** após os retries; **relatório assíncrono** (202 → READY)
  com saldo correto e Excel válido (lido com Apache POI); relatório de outro usuário invisível
  (404); período inválido → 400; sem token → 401.

**gateway-service / server-service**: rotas `lb://finora` e `lb://reports-service`; health; registro do Eureka.

## Como executar

```powershell
# Backend (precisa do Docker rodando, por causa do Testcontainers)
cd finora;          .\mvnw verify
cd ..\reports-service; .\mvnw verify
# unitários apenas (sem Docker): .\mvnw test -Dtest='*Test'
# cobertura: target\site\jacoco\index.html

# Front-end
cd front_finora\finora
npm test

# Sistema implantado (bash/WSL)
BASE_URL=http://localhost:8080 ./deploy/scripts/smoke-test.sh
```

Os testes de integração usam o profile `test` (`src/test/resources/application-test.yaml`):
Eureka e exportação de traces desligados, segredo JWT próprio de teste e retries rápidos.
