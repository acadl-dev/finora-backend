# CI/CD — GitHub Actions

Cada repositório tem um workflow que roda a cada *push* e *pull request*:

| Repositório | Workflow | Arquivo |
|---|---|---|
| `finora-backend` | CI/CD backend | `.github/workflows/ci-cd.yml` |
| `finora-frontend` | CI/CD frontend | `.github/workflows/ci-cd.yml` |

## 1. Pipeline do backend

```
push / pull request
   │
   ├─► test (matriz: server-service, gateway-service, finora, reports-service) ─┐
   │     ./mvnw verify → unitários + integração (Testcontainers) + JaCoCo       │
   │                                                                            ├─► images ──► deploy
   └─► manifests: kustomize + kubeconform (overlays local e ci) + compose ──────┘    (só main, tag v* ou manual)
```

| Job | Quando | O que faz | Falha se… |
|---|---|---|---|
| **test** | Sempre | Para cada microsserviço, em paralelo: `./mvnw -B verify` — testes unitários (Surefire), de integração com PostgreSQL e RabbitMQ reais em containers (Failsafe + Testcontainers) e relatório de cobertura (JaCoCo). Publica o resumo na página da execução e os relatórios como *artifacts* | Qualquer teste falhar ou o código não compilar |
| **manifests** | Sempre | Renderiza os overlays `local` e `ci` com `kubectl kustomize`, valida o esquema com `kubeconform` e valida o `docker-compose.yml` | Manifest inválido ou referência quebrada |
| **images** | `main`, tags `v*` ou execução manual, após *test* e *manifests* | Constrói a imagem de cada serviço (Dockerfile multi-stage, cache do GitHub Actions) e publica no **GitHub Container Registry** | Build da imagem falhar |
| **deploy** | Após *images* | Cria um cluster Kubernetes efêmero (**kind**), carrega as imagens recém-publicadas, gera segredos aleatórios, aplica `deploy/k8s/overlays/ci`, espera o *rollout* de todos os componentes e executa o **smoke test ponta a ponta** pelo gateway | Algum pod não ficar pronto ou o fluxo cadastro → transação → evento → relatório → Excel falhar. Em falha, imprime pods, eventos e logs |

### Versionamento das imagens

`docker/metadata-action` gera as tags automaticamente:

| Evento | Tags publicadas (ex.: `ghcr.io/acadl-dev/finora`) |
|---|---|
| Push na `main` | `sha-3f2a9c1`, `main`, `latest` |
| Tag `v5.0.0` | `sha-3f2a9c1`, `5.0.0` |
| Execução manual em outra branch | `sha-3f2a9c1`, `<branch>` |

A tag `sha-*` liga cada imagem ao commit exato que a gerou, o que permite rollback preciso
(`kubectl set image ... :sha-<commit anterior>`).

### Segredos

Nenhum segredo manual é necessário: o workflow usa o `GITHUB_TOKEN` para publicar no GHCR
(permissão `packages: write`) e gera senhas e `JWT_SECRET` aleatórios para o cluster de teste.

## 2. Pipeline do front-end

| Job | Quando | Passos |
|---|---|---|
| **ci** | Sempre | `npm ci` → `npm run lint` → `npm run typecheck` → `npm test` (Vitest) → `npm run build` (standalone) |
| **image** | `main`, tags `v*` ou manual | Build e push de `ghcr.io/acadl-dev/finora-frontend` com as mesmas regras de tag |

## 3. Como usar

- **Ver resultados:** aba **Actions** do repositório → execução → resumo com número de testes e
  cobertura de cada serviço; *artifacts* com os relatórios HTML do JaCoCo.
- **Disparar manualmente:** Actions → *CI/CD backend* → **Run workflow**.
- **Liberar uma versão:** `git tag v5.0.0 && git push origin v5.0.0`.
- **Proteger a `main`** (recomendado): *Settings → Branches → Add rule* → exigir PR e os checks
  `Testes — *` e `Valida Kubernetes e Docker Compose` antes do merge.

## 4. Manutenção automatizada

`.github/dependabot.yml` abre PRs semanais atualizando dependências Maven, imagens base dos
Dockerfiles e versões das actions. Cada PR passa pelo mesmo CI antes do merge.

## 5. Executar as mesmas etapas localmente

```bash
cd finora && ./mvnw verify                         # testes + cobertura (target/site/jacoco/index.html)
kubectl kustomize deploy/k8s/overlays/local        # renderiza os manifests
./deploy/scripts/build-images.sh                   # imagens
BASE_URL=http://localhost:8080 ./deploy/scripts/smoke-test.sh
```
