[CmdletBinding()]
param([ValidateSet('clinic-phase1')][string]$Context = 'clinic-phase1', [int]$Port = 16969)
$ErrorActionPreference = 'Stop'
Write-Output "Open http://localhost:$Port/sabyeguyz/ ; keep this window running during the presentation."
& kubectl --context $Context -n kube-system port-forward service/traefik "${Port}:80" --address 127.0.0.1
if ($LASTEXITCODE -ne 0) { throw 'Traefik port-forward stopped with an error' }
