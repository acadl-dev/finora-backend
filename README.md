# Finora — Backend

[![CI/CD backend](https://github.com/acadl-dev/finora-backend/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/acadl-dev/finora-backend/actions/workflows/ci-cd.yml)

Sistema de controle financeiro pessoal (Projeto de Bloco — Engenharia de Softwares Escaláveis):
microsserviços Spring Boot com Domain-Driven Design, arquitetura orientada a eventos (RabbitMQ),
conteinerizados com Docker, orquestrados no Kubernetes, monitorados com Grafana/Loki/Tempo/Prometheus
e entregues por CI/CD no GitHub Actions. O front-end está em
[finora-frontend](https://github.com/acadl-dev/finora-frontend).

## Arquitetura

| Serviço | Porta | Papel |
|---|---|---|
| `server-service` | 8761 | **Service Discovery** (Spring Cloud Netflix Eureka) |
| `gateway-service` | 8080 | **API Gateway** (Spring Cloud Gateway) — porta única do front |
| `finora` | 8082 | Contexto de **Identidade e Transações**. PostgreSQL próprio. **Produtor** de eventos (Transactional Outbox) |
| `reports-service` | 8083 | Contexto de **Relatórios**. PostgreSQL próprio. **Consumidor** de eventos + workers de relatório |
| RabbitMQ | 5672 / 15672 | Message broker |
| Loki · Tempo · Prometheus · Grafana | — / — / 9090 / 3001 | Logs · traces · métricas · painéis |

```
Front (Next.js) ──► gateway-service ──┬─► /auth/**, /transactions/** ─► finora ──► PostgreSQL
                          │           └─► /reports/**                 ─► reports-service ──► PostgreSQL
                          ▼                                                   ▲
                  server-service (Eureka)            finora ── eventos ──► RabbitMQ
                                                    (outbox)   comandos de relatório (workers)

Todos ──► logs: Loki · traces: Tempo · métricas: Prometheus ──► Grafana
```

Stack: Java 21, Spring Boot 3.5, Spring Cloud 2025.0, Spring AMQP, PostgreSQL 13, RabbitMQ 4,
Micrometer + OpenTelemetry, Docker, Kubernetes (Kustomize), GitHub Actions.

Diagramas de componentes, de sequência, modelo de dados, bounded contexts (DDD) e prós e contras da
arquitetura orientada a eventos: **[docs/arquitetura.md](docs/arquitetura.md)**.

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/arquitetura.md](docs/arquitetura.md) | Domínio (DDD), diagramas de componentes e de sequência, topologia do RabbitMQ, modelo de dados, histórico de dados, prós e contras da EDA |
| [docs/implantacao.md](docs/implantacao.md) | Docker, Kubernetes (Docker Desktop), escalabilidade, rolling update, rollback, problemas comuns |
| [docs/monitoramento.md](docs/monitoramento.md) | Agregação de logs, rastreamento de transações, métricas, painel e health checks |
| [docs/ci-cd.md](docs/ci-cd.md) | Pipelines do GitHub Actions, versionamento das imagens, deploy automatizado |
| [docs/testes.md](docs/testes.md) | Estratégia e casos de teste (unitários, integração, componente, E2E, carga) |
| [docs/versionamento.md](docs/versionamento.md) | Branches, commits, tags e fluxo de pull request |
| [CHANGELOG.md](CHANGELOG.md) | Mudanças por versão |

## Início rápido — produção simulada no Kubernetes

Pré-requisito: Docker Desktop com Kubernetes ativado. Detalhes em [docs/implantacao.md](docs/implantacao.md).

```powershell
.\deploy\scripts\build-images.ps1
Copy-Item deploy\k8s\overlays\local\secrets.env.example deploy\k8s\overlays\local\secrets.env   # ajuste JWT_SECRET
kubectl apply -k deploy\k8s\overlays\local
kubectl get pods -n finora -w
```

| Aplicação | Grafana | Eureka | RabbitMQ | Prometheus |
|---|---|---|---|---|
| http://localhost:3000 | http://localhost:3001 | http://localhost:8761 | http://localhost:15672 | http://localhost:9090 |

Sem Kubernetes: `cd deploy/docker && cp .env.example .env && docker compose up -d --build`.

## Desenvolvimento local (IntelliJ)

1. Infraestrutura: `docker compose up -d` (RabbitMQ, nesta pasta), depois `docker compose up -d`
   em `finora/` (PostgreSQL 5433) e em `reports-service/` (PostgreSQL 5434).
2. Serviços, nesta ordem: `server-service` → `reports-service` → `finora` → `gateway-service`
   (`./mvnw spring-boot:run` ou pelo IntelliJ). Cada serviço lê o `JWT_SECRET` do seu `.env`.
3. Front: `front_finora/finora` → `npm run dev` (`API_BASE_URL=http://localhost:8080`).

Para ver logs e traces também no desenvolvimento, suba a observabilidade do compose de produção
(`docker compose -f deploy/docker/docker-compose.yml up -d loki tempo prometheus grafana`) e rode os
serviços com o profile `loki` e a variável `TRACING_SAMPLING_PROBABILITY=1.0`.

## Endpoints (via gateway)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | Cadastro e login (JWT) |
| POST · GET · DELETE | `/transactions`, `/transactions/{id}` | Receitas e despesas (eventos na outbox) |
| POST | `/reports` | Pede o relatório Excel (202 Accepted, geração assíncrona) |
| GET | `/reports/{id}`, `/reports/{id}/file`, `/reports/history` | Status, download e histórico |
| GET | `/actuator/health`, `/actuator/prometheus` (em cada serviço) | Health checks e métricas |

## Testes

```powershell
cd finora; .\mvnw verify          # unitários + integração (Testcontainers: precisa do Docker)
```

Resumo por camada em [docs/testes.md](docs/testes.md). O CI roda tudo a cada push.
