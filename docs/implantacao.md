# Implantação — Docker e Kubernetes

Este guia coloca o Finora em um **ambiente de produção simulado**: todos os microsserviços em
containers, orquestrados pelo Kubernetes do Docker Desktop, com banco de dados, mensageria e
observabilidade dentro do cluster.

## 1. Visão da implantação

| Componente | Tipo no Kubernetes | Réplicas | Acesso externo (Docker Desktop) |
|---|---|---|---|
| `frontend` (Next.js) | Deployment + Service LoadBalancer | 2 | http://localhost:3000 |
| `gateway-service` | Deployment + Service LoadBalancer | 2 | http://localhost:8080 |
| `server-service` (Eureka) | Deployment + Service LoadBalancer | 1 | http://localhost:8761 |
| `finora` | Deployment + HPA (2→5) + PDB | 2 a 5 | só pelo gateway |
| `reports-service` | Deployment + HPA (2→5) + PDB | 2 a 5 | só pelo gateway |
| `postgres-finora`, `postgres-reports` | StatefulSet + volume persistente (1 Gi) | 1 | interno |
| `rabbitmq` | StatefulSet + volume persistente (1 Gi) | 1 | painel http://localhost:15672 |
| `loki`, `tempo` | Deployment | 1 | interno (vistos pelo Grafana) |
| `prometheus` | Deployment + RBAC | 1 | http://localhost:9090 |
| `grafana` | Deployment | 1 | http://localhost:3001 |

Tudo roda no namespace **`finora`**.

### Estrutura dos arquivos

```
back_finora/
├── */Dockerfile                  # imagem de cada microsserviço (multi-stage, JRE 21, usuário sem root)
├── deploy/
│   ├── docker/docker-compose.yml # pilha completa só com Docker (alternativa ao Kubernetes)
│   ├── k8s/
│   │   ├── base/                 # namespace, ConfigMap, bancos, RabbitMQ, microsserviços, HPA/PDB
│   │   ├── observability/        # Loki, Tempo, Prometheus, Grafana (+ configs e painel)
│   │   ├── frontend/             # front-end Next.js
│   │   ├── overlays/local/       # produção simulada no Docker Desktop (tudo)
│   │   ├── overlays/ci/          # usado pelo pipeline de CD (cluster kind no GitHub Actions)
│   │   └── addons/               # metrics-server (necessário para o HPA)
│   └── scripts/                  # build das imagens, smoke test e teste de carga
```

Os manifests usam **Kustomize** (embutido no `kubectl`): a *base* é reaproveitada e cada
*overlay* ajusta o ambiente (segredos, tags de imagem, réplicas).

## 2. Adaptações feitas no código para operar em containers

| Adaptação | Onde | Por quê |
|---|---|---|
| Configuração por variáveis de ambiente | `application.yaml` (`${VAR:padrão}`) + ConfigMap/Secret | A mesma imagem roda em dev, Docker e Kubernetes (12-factor) |
| Health checks liveness/readiness | Spring Boot Actuator (`/actuator/health/*`) | O Kubernetes reinicia pods travados e só envia tráfego a pods prontos |
| Readiness inclui o banco, não o RabbitMQ | `management.endpoint.health.group.readiness` | Broker fora do ar não derruba a API (a outbox segura os eventos) |
| Registro no Eureka por IP | `EUREKA_PREFER_IP=true` | Cada réplica aparece separada no Eureka e recebe tráfego do gateway |
| Desligamento gracioso | `server.shutdown: graceful` + `preStop` + `terminationGracePeriodSeconds` | Rolling update sem derrubar requisições em andamento |
| JVM ciente do container | `JAVA_OPTS=-XX:MaxRAMPercentage=75` | Heap respeita o limite de memória do pod |
| Segredos fora da imagem | `.dockerignore` exclui `.env`; Secret `finora-secrets` | Imagens podem ser públicas sem vazar credenciais |
| Front com saída *standalone* | `next.config.ts` (`output: "standalone"`) + `/api/health` | Imagem Node mínima e health check para os probes |

## 3. Pré-requisitos

1. **Docker Desktop** com Kubernetes ativado: *Settings → Kubernetes → Enable Kubernetes*
   (método de provisionamento **kubeadm**, que enxerga as imagens construídas localmente).
   Recomendado: 6 GB+ de memória para o Docker (*Settings → Resources*).
2. `kubectl` (vem com o Docker Desktop). Confira: `kubectl config use-context docker-desktop`.
3. Pare o ambiente de desenvolvimento (containers do `docker compose` e serviços no IntelliJ):
   as portas 3000, 8080, 8761 e 15672 são as mesmas.

## 4. Passo a passo no Kubernetes (Docker Desktop)

Todos os comandos na pasta `back_finora` (PowerShell).

```powershell
# 1) Construir as imagens (4 microsserviços + front) com a tag :local
.\deploy\scripts\build-images.ps1

# 2) Segredos: copie o exemplo e ajuste (o arquivo secrets.env NÃO é versionado)
Copy-Item deploy\k8s\overlays\local\secrets.env.example deploy\k8s\overlays\local\secrets.env
#    edite JWT_SECRET (pode reutilizar o valor do finora/.env)

# 3) metrics-server para o HPA (apenas na primeira vez) — ver deploy/k8s/addons/README.md
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json --patch-file deploy\k8s\addons\metrics-server-insecure-tls.json

# 4) Implantar tudo
kubectl apply -k deploy\k8s\overlays\local

# 5) Acompanhar a subida (2 a 4 minutos)
kubectl get pods -n finora -w
```

Quando todos os pods estiverem `Running` e `READY 1/1`:

| O quê | Endereço |
|---|---|
| Aplicação | http://localhost:3000 |
| Grafana (admin / senha do secrets.env) | http://localhost:3001 |
| Eureka | http://localhost:8761 |
| RabbitMQ (finora / senha do secrets.env) | http://localhost:15672 |
| Prometheus | http://localhost:9090 |

Validação automática ponta a ponta (bash/WSL): `./deploy/scripts/smoke-test.sh`

## 5. Operação do dia a dia

```powershell
kubectl config set-context --current --namespace=finora   # evita repetir "-n finora"

kubectl get pods,svc,hpa                     # visão geral
kubectl logs deploy/finora -f                # logs de um serviço (todas as linhas também vão ao Loki)
kubectl describe pod <nome-do-pod>           # eventos e probes de um pod
kubectl top pods                             # CPU/memória (metrics-server)

# Escalar manualmente (o HPA volta a ajustar conforme a CPU)
kubectl scale deployment reports-service --replicas=4

# Atualizar uma versão (rolling update sem indisponibilidade: maxUnavailable=0)
kubectl set image deployment/finora finora=ghcr.io/acadl-dev/finora:sha-abc1234
kubectl rollout status deployment/finora
kubectl rollout history deployment/finora
kubectl rollout undo deployment/finora       # rollback para a versão anterior

# Auto-recuperação: apague um pod e veja o Kubernetes recriá-lo
kubectl delete pod -l app=finora --wait=false; kubectl get pods -w
```

### Escalabilidade automática (HPA)

`finora` e `reports-service` têm um **HorizontalPodAutoscaler**: mínimo 2, máximo 5 réplicas,
alvo de 70% de CPU. Para ver funcionando, gere carga com o k6:

```powershell
Get-Content deploy\scripts\load-test.js | docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run -
kubectl get hpa -w
```

Os **PodDisruptionBudgets** garantem ao menos 1 réplica de `finora`, `reports-service` e
`gateway-service` disponível durante manutenções.

### Remover tudo

```powershell
kubectl delete -k deploy\k8s\overlays\local   # remove também os volumes (dados)
```

## 6. Alternativa: pilha completa com Docker Compose

Útil para máquinas sem Kubernetes ativo. Usa as mesmas imagens e configurações de observabilidade.

```powershell
cd deploy\docker
Copy-Item .env.example .env      # ajuste JWT_SECRET
docker compose up -d --build
docker compose ps
```

## 7. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `ErrImageNeverPull` / `ImagePullBackOff` com tag `:local` | Imagem não construída ou cluster em modo *kind* | Rode `build-images.ps1`; use o provisionamento *kubeadm* no Docker Desktop |
| Pod em `Init:0/1` por muito tempo | Aguardando banco/RabbitMQ/Eureka (initContainer) | Normal nos primeiros minutos; veja `kubectl logs <pod> -c wait-dependencies` |
| `CrashLoopBackOff` no finora/reports | Segredo ausente ou inválido | Confira `secrets.env` (JWT_SECRET em Base64 com 32+ bytes) e reaplique |
| HPA mostra `<unknown>` | metrics-server ausente | Passo 3 da seção 4 |
| Porta 8080/3000/15672 ocupada | Ambiente de desenvolvimento ainda rodando | Pare os containers de dev e os serviços no IntelliJ |
| Gateway responde 503 logo após subir | Eureka ainda propagando o registro | Aguarde ~30 s |
