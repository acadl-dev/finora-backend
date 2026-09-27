# Addons do cluster local (Docker Desktop)

O HPA precisa do **metrics-server** para ler o uso de CPU dos pods. O Docker Desktop não o
instala por padrão. Uma única vez:

```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json --patch-file deploy/k8s/addons/metrics-server-insecure-tls.json
kubectl top nodes   # após ~1 min deve mostrar CPU/memória
```

O patch adiciona `--kubelet-insecure-tls`, necessário porque o kubelet do Docker Desktop usa
certificado autoassinado (apenas para ambiente local).
