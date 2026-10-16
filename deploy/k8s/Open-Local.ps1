param([string]$Context = 'docker-desktop', [int]$Port = 18088)
. "$PSScriptRoot/Common.ps1"
Assert-LocalContext $Context
Write-Host "Clinic Kubernetes: http://localhost:$Port (Ctrl+C stops forwarding only)"
Invoke-Kube -n clinic-local port-forward --address=127.0.0.1 service/clinic-ingress "${Port}:80"
