#!/usr/bin/env bash
# Constrói as imagens Docker de todos os microsserviços (e do front, se encontrado)
# com a tag ":local", usada pelos manifests do Kubernetes (deploy/k8s).
# Uso (bash/WSL, na pasta back_finora):  ./deploy/scripts/build-images.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REGISTRY="ghcr.io/acadl-dev"

for svc in server-service gateway-service finora reports-service; do
  echo "==> $svc"
  docker build -t "$REGISTRY/$svc:local" "$ROOT/$svc"
done

FRONT="$ROOT/../front_finora/finora"
if [ -d "$FRONT" ]; then
  echo "==> finora-frontend"
  docker build -t "$REGISTRY/finora-frontend:local" "$FRONT"
else
  echo "Aviso: front-end não encontrado em $FRONT"
fi

docker images "$REGISTRY/*"
