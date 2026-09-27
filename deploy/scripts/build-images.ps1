# Constrói as imagens Docker de todos os microsserviços (e do front, se encontrado)
# com a tag ":local", usada pelos manifests do Kubernetes (deploy/k8s).
# Uso (PowerShell, na pasta back_finora):  .\deploy\scripts\build-images.ps1
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
$registry = "ghcr.io/acadl-dev"

foreach ($svc in @("server-service", "gateway-service", "finora", "reports-service")) {
    Write-Host "==> $svc" -ForegroundColor Cyan
    docker build -t "$registry/${svc}:local" "$root\$svc"
    if ($LASTEXITCODE -ne 0) { throw "Falha ao construir $svc" }
}

$front = Join-Path $root "..\front_finora\finora"
if (Test-Path $front) {
    Write-Host "==> finora-frontend" -ForegroundColor Cyan
    docker build -t "$registry/finora-frontend:local" $front
    if ($LASTEXITCODE -ne 0) { throw "Falha ao construir o front-end" }
} else {
    Write-Warning "Front-end não encontrado em $front (imagem finora-frontend não construída)"
}

docker images "$registry/*"
