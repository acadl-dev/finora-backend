# Monitoramento — logs, rastreamento e métricas

O Finora usa a pilha **Grafana LGTM**: **L**oki (logs), **G**rafana (painéis), **T**empo (traces)
e Prometheus (**M**étricas). Tudo é visto em um só lugar: **http://localhost:3001**
(usuário `admin`, senha `GRAFANA_ADMIN_PASSWORD` do `secrets.env`).

```
                 ┌──────────── logs (HTTP, appender loki4j) ────────────► Loki  ─┐
 finora          │                                                               │
 reports-service ├──────────── traces (OTLP HTTP :4318) ────────────────► Tempo ─┼──► Grafana :3001
 gateway-service │                                                               │
 server-service  └──── /actuator/prometheus ◄── coleta a cada 15 s ─── Prometheus ┘
```

## 1. Agregação de logs (Loki)

| Item | Configuração |
|---|---|
| Como os logs chegam | Appender `loki4j` no `logback-spring.xml` de cada serviço, ativado pelo profile `loki` (`SPRING_PROFILES_ACTIVE=loki` no ConfigMap) |
| Rótulos (labels) | `app` (nome do serviço), `host` (pod) e `level` |
| Correlação | Toda linha traz `traceId=... spanId=...` (Micrometer Tracing preenche o MDC) |
| Retenção | 72 h (`deploy/k8s/observability/config/loki.yaml`) |
| Console | Continua ativo: `kubectl logs deploy/finora` funciona como antes |

**Consultas úteis** (Grafana → *Explore* → *Loki*):

```logql
{app="finora"}                                   # todos os logs do finora
{app=~".+", level="ERROR"}                       # erros de todos os serviços
{app="reports-service"} |= "Relatório"           # relatórios gerados
{app=~".+"} |= "<traceId>"                       # tudo de UMA transação, em todos os serviços
sum by (app) (count_over_time({level="WARN"}[5m]))  # volume de avisos por serviço
```

Em cada linha, o campo **traceId** vira um link **"Ver trace no Tempo"** (campo derivado configurado
em `grafana-datasources.yaml`).

## 2. Rastreamento distribuído de transações (Tempo)

| Item | Configuração |
|---|---|
| Instrumentação | Micrometer Tracing + ponte OpenTelemetry (`micrometer-tracing-bridge-otel`) |
| Exportação | OTLP HTTP para `http://tempo:4318/v1/traces` (`OTLP_TRACING_ENDPOINT`) |
| Amostragem | 100% em produção (`TRACING_SAMPLING_PROBABILITY=1.0` no ConfigMap/compose); 0% no desenvolvimento local, onde não há Tempo |
| Propagação HTTP | Cabeçalho W3C `traceparent`: gateway → finora / reports-service |
| Propagação RabbitMQ | `spring.rabbitmq.template/listener.observation-enabled: true` |
| Propagação pela Outbox | `OutboxTracing` guarda o `traceparent` junto do evento e o relay publica dentro do mesmo trace |

**O que isso permite:** uma única transação — o usuário cadastrar uma despesa — aparece como
**um trace** atravessando os serviços:

```
gateway-service  POST /transactions
 └─ finora        http post /transactions  →  INSERT transactions + outbox_events
     └─ finora    outbox publish TransactionRegistered   (relay, até 1 s depois)
         └─ finora            finora.transactions send
             └─ reports-service  reports.transaction-events receive → INSERT ledger_entries
```

Sem o `OutboxTracing`, a publicação (feita por uma thread agendada) começaria um trace novo e a
ligação entre a requisição HTTP e o consumo no reports-service se perderia.

**Como ver:** Grafana → *Explore* → *Tempo* → *Search* → `Service Name = gateway-service`
(ou cole um `traceId` copiado de um log). Em um span, **"Logs for this span"** abre no Loki as
linhas daquela transação.

## 3. Métricas (Prometheus)

Cada serviço expõe `/actuator/prometheus` (Micrometer). No Kubernetes, o Prometheus descobre os
pods sozinho pela anotação `prometheus.io/scrape: "true"` (RBAC em `observability/prometheus.yaml`);
o RabbitMQ também é coletado (`/metrics/per-object`, profundidade de cada fila).

| Métrica | Origem | Significado |
|---|---|---|
| `http_server_requests_seconds_*` | Spring MVC/WebFlux | Taxa, erros e latência (histograma → p95) por serviço |
| `jvm_memory_used_bytes`, `process_cpu_usage` | JVM | Memória e CPU de cada réplica |
| `finora_outbox_pending` | **custom** — finora | Eventos na outbox aguardando o RabbitMQ (deve ficar em 0) |
| `finora_outbox_published_total`, `finora_outbox_publish_failures_total` | **custom** — finora | Publicações e falhas de publicação por tipo de evento |
| `finora_ledger_events_applied_total`, `finora_ledger_events_duplicated_total` | **custom** — reports | Eventos aplicados e duplicatas descartadas (idempotência) |
| `finora_reports_completed_total{status}` | **custom** — reports | Relatórios concluídos (READY) e com falha (FAILED) |
| `rabbitmq_queue_messages_ready{queue}` | RabbitMQ | Mensagens esperando em cada fila, inclusive DLQs |

Todas as métricas dos serviços têm o rótulo `application` (`management.metrics.tags.application`).

## 4. Painel "Finora — Operação dos microsserviços"

Provisionado automaticamente (é a tela inicial do Grafana). Arquivo:
`deploy/k8s/observability/config/dashboards/finora-overview.json`.

| Seção | Painéis | Para detectar |
|---|---|---|
| Visão geral | Pods no ar, requisições/s, erros 5xx/s, eventos pendentes na outbox | Saúde geral em 5 segundos |
| Tráfego HTTP (RED) | Requisições/s, latência p95, erros 4xx/5xx por serviço, réplicas por serviço | Lentidão, erros e o HPA escalando |
| Arquitetura orientada a eventos | Publicados × aplicados, mensagens nas filas (e DLQs), relatórios concluídos, falhas de publicação | Broker fora, consumidor atrasado, mensagens na DLQ |
| JVM e logs | Heap e CPU por serviço; logs WARN/ERROR de todos os serviços | Vazamento de memória, exceções |

## 5. Health checks

| Endpoint | Usado por | Considera |
|---|---|---|
| `/actuator/health/liveness` | `livenessProbe` e `startupProbe` | A aplicação está viva (senão, o pod é reiniciado) |
| `/actuator/health/readiness` | `readinessProbe` | Pronta + banco acessível (senão, sai do balanceamento) |
| `/actuator/health` | Humanos / diagnóstico | Detalhes: banco, RabbitMQ, disco, Eureka |
| `/api/health` (front) | Probes do front | Servidor Next.js respondendo |

## 6. Roteiro de diagnóstico

1. **Painel** mostra erro ou latência alta em um serviço.
2. **Logs** do período: `{app="<serviço>", level="ERROR"}` → abrir a linha → clicar no `traceId`.
3. **Trace** mostra em qual serviço/etapa o tempo foi gasto ou a exceção ocorreu.
4. **Kubernetes**: `kubectl describe pod` / `kubectl logs` para reinícios, probes e limites de memória.

## 7. Arquivos de configuração

| Arquivo | Conteúdo |
|---|---|
| `*/src/main/resources/logback-spring.xml` | Console + appender Loki (profile `loki`) |
| `*/src/main/resources/application.yaml` (bloco `management`) | Actuator, probes, histogramas, tracing, OTLP |
| `finora/.../outbox/service/OutboxTracing.java` | Propagação do trace pela outbox |
| `finora/.../outbox/service/OutboxMetrics.java` | Métrica `finora_outbox_pending` |
| `deploy/k8s/observability/config/*.yaml` | Loki, Tempo, Prometheus, fontes de dados e painéis do Grafana |
| `deploy/docker/prometheus.yml` | Alvos do Prometheus no Docker Compose |
