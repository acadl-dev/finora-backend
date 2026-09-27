# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e
[versionamento semântico](https://semver.org/lang/pt-BR/).

## [5.0.0] — TP5: implantação e manutenção em produção

### Adicionado
- **Docker:** Dockerfile multi-stage (JDK 21 → JRE 21, usuário sem root, healthcheck) para os 4
  microsserviços e para o front; `deploy/docker/docker-compose.yml` com a pilha completa.
- **Kubernetes** (`deploy/k8s`, Kustomize): Deployments com probes, rolling update sem
  indisponibilidade, StatefulSets com volumes para PostgreSQL e RabbitMQ, ConfigMap/Secret,
  HPA (2→5 réplicas) e PodDisruptionBudgets; overlays `local` (Docker Desktop) e `ci` (kind).
- **Observabilidade:** logs no Loki (loki4j), traces no Tempo (Micrometer Tracing + OpenTelemetry,
  propagados por HTTP, RabbitMQ e pela outbox), métricas no Prometheus e painel no Grafana;
  métricas de negócio (`finora_outbox_pending`, eventos, relatórios).
- **Testes:** unitários de domínio do finora; integração com PostgreSQL e RabbitMQ reais
  (Testcontainers) no finora e no reports-service; testes de componente do gateway e do Eureka;
  testes do front (Vitest); smoke test ponta a ponta; teste de carga (k6); cobertura JaCoCo.
- **CI/CD:** GitHub Actions — testes em matriz, validação de manifests, imagens no GHCR e deploy
  em cluster kind com smoke test; Dependabot; modelo de pull request.
- Front: endpoint `/api/health`, saída `standalone`, rota de logout.

### Alterado
- Configurações externalizadas por variáveis de ambiente; readiness considera o banco, não o broker.
- Registro no Eureka por IP (réplicas distintas) e desligamento gracioso.

## [3.0.0] — TP3/TP4: arquitetura orientada a eventos
- RabbitMQ, Transactional Outbox, eventos `TransactionRegistered`/`TransactionRemoved`, projeção
  `ledger_entries`, relatório assíncrono (comando + workers), retry e DLQ, consumidor idempotente.
- Exclusão de transações.

## [2.0.0] — TP2: microsserviços
- `server-service` (Eureka), `gateway-service` (Spring Cloud Gateway) e `reports-service` com banco
  próprio; exportação de relatório em Excel.

## [1.0.0] — TP1: domínio e cadastro
- Autenticação JWT; cadastro de receitas e despesas com modelo de domínio rico (DDD).
