# Gestão de configuração e versionamento (Git e GitHub)

## Repositórios

| Repositório | Conteúdo |
|---|---|
| [`acadl-dev/finora-backend`](https://github.com/acadl-dev/finora-backend) | 4 microsserviços, manifests Kubernetes, compose, pipelines e documentação |
| [`acadl-dev/finora-frontend`](https://github.com/acadl-dev/finora-frontend) | Front-end Next.js, Dockerfile e pipeline |

## Estratégia de branches

| Branch | Uso |
|---|---|
| `main` | Versão estável. Todo push dispara o CD (imagens no GHCR + deploy de teste no Kubernetes) |
| `tp1` … `tp5` | Uma branch por entrega do Projeto de Bloco; vira PR para a `main` ao final da etapa |
| `feature/<assunto>`, `fix/<assunto>` | Trabalho do dia a dia, integrado via pull request |

Fluxo: `feature/*` → PR para a branch da etapa (`tp5`) → PR `tp5` → `main` → tag `v5.0.0`.
Os PRs usam o modelo `.github/pull_request_template.md` e só são mesclados com o CI verde.

## Mensagens de commit (Conventional Commits)

```
<tipo>(<escopo>): <resumo no imperativo>

feat(reports): gera relatório de forma assíncrona via RabbitMQ
fix(finora): corrige leitura do .env fora da pasta do serviço
ci: adiciona deploy em cluster kind com smoke test
docs: documenta implantação no Kubernetes
test(reports): cobre idempotência e DLQ com Testcontainers
```

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `ci`, `build`, `chore`, `perf`.

## Versões (tags semânticas)

| Tag | Entrega |
|---|---|
| `v1.0.0` | TP1 — monólito: autenticação e cadastro de receitas/despesas (DDD) |
| `v2.0.0` | TP2 — microsserviços: Eureka, Gateway, reports-service com banco próprio |
| `v3.0.0` | TP3/TP4 — arquitetura orientada a eventos com RabbitMQ |
| `v5.0.0` | TP5 — Docker, Kubernetes, observabilidade, CI/CD e testes abrangentes |
| `v5.1.0` | AT — documentação completa da arquitetura (diagramas de componentes e de sequência) |

As mudanças de cada versão estão no [CHANGELOG](../CHANGELOG.md). Cada imagem Docker também
é versionada (tag `sha-<commit>` e a versão semântica), ligando o que roda no cluster ao código.

## O que nunca vai para o Git

`.env`, `secrets.env`, `deploy/docker/.env` e `target/` estão no `.gitignore`. Os segredos são
fornecidos em tempo de execução (Secret do Kubernetes, variáveis de ambiente, `GITHUB_TOKEN`).

## Registro desta entrega (TP5)

Commits sugeridos, um por assunto, na branch `tp5`:

```bash
git checkout -b tp5
git add */pom.xml */src/main/resources */src/main/java
git commit -m "feat: prepara microsserviços para produção (actuator, métricas, tracing e logs no Loki)"
git add */Dockerfile */.dockerignore deploy/docker deploy/scripts/build-images.*
git commit -m "build: conteineriza os microsserviços e adiciona compose de produção simulada"
git add deploy/k8s
git commit -m "feat(k8s): manifests kustomize com HPA, PDB, probes e observabilidade"
git add */src/test deploy/scripts/smoke-test.sh deploy/scripts/load-test.js
git commit -m "test: integração com Testcontainers, smoke test ponta a ponta e teste de carga"
git add .github .gitattributes .gitignore
git commit -m "ci: pipeline de CI/CD com GHCR e deploy em cluster kind"
git add README.md CHANGELOG.md docs
git commit -m "docs: implantação, monitoramento, CI/CD, testes e versionamento"
git push -u origin tp5
# abrir PR tp5 -> main no GitHub; após o merge:
git checkout main && git pull && git tag v5.0.0 && git push origin v5.0.0
```
