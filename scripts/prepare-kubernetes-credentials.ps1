param(
    [string]$Context = 'docker-desktop',
    [string]$Namespace = 'clinic-present',
    [string]$AgentRoot = (Join-Path $env:USERPROFILE 'Desktop\ClinicJenkinsAgent')
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$kubectl = Get-Command kubectl -ErrorAction Stop

function Invoke-AdminKubectl {
    & $kubectl.Source --context $Context @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($args -join ' ')" }
}

Invoke-AdminKubectl get nodes -o wide
$namespaceExists = & $kubectl.Source --context $Context get namespace $Namespace --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the Kubernetes namespace.' }
if (-not $namespaceExists) { Invoke-AdminKubectl create namespace $Namespace }

$rbacFile = Join-Path $repoPath 'deploy/kubernetes/jenkins-rbac.yaml'
$rbac = [IO.File]::ReadAllText($rbacFile).Replace('namespace: clinic-present', "namespace: $Namespace")
$tempManifest = Join-Path ([IO.Path]::GetTempPath()) ('clinic-rbac-' + [guid]::NewGuid().ToString('N') + '.yaml')
try {
    [IO.File]::WriteAllText($tempManifest, $rbac, (New-Object Text.UTF8Encoding($false)))
    Invoke-AdminKubectl apply -f $tempManifest
} finally {
    if (Test-Path -LiteralPath $tempManifest) { Remove-Item -LiteralPath $tempManifest }
}

$token = ''
for ($attempt = 0; $attempt -lt 30 -and -not $token; $attempt++) {
    $token = (& $kubectl.Source --context $Context -n $Namespace get secret clinic-deployer-token -o 'jsonpath={.data.token}')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the scoped deployer token.' }
    if (-not $token) { Start-Sleep -Seconds 2 }
}
if (-not $token) { throw 'Kubernetes did not populate the ServiceAccount token secret.' }
$token = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($token))

$baseConfigJson = & $kubectl.Source config view --raw --minify --context $Context -o json
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the selected Kubernetes context.' }
$baseConfig = $baseConfigJson | ConvertFrom-Json
$clusterName = $baseConfig.contexts[0].context.cluster
$cluster = $baseConfig.clusters | Where-Object { $_.name -eq $clusterName } | Select-Object -First 1
if (-not $cluster.cluster.server) { throw 'The selected Kubernetes context has no API server URL.' }

$scopedConfig = [ordered]@{
    apiVersion = 'v1'
    kind = 'Config'
    clusters = @(@{ name = $clusterName; cluster = $cluster.cluster })
    users = @(@{ name = 'clinic-deployer'; user = @{ token = $token } })
    contexts = @(@{ name = 'clinic-deployer'; context = @{ cluster = $clusterName; user = 'clinic-deployer'; namespace = $Namespace } })
    'current-context' = 'clinic-deployer'
}
[IO.Directory]::CreateDirectory($AgentRoot) | Out-Null
$kubeconfigPath = Join-Path $AgentRoot 'clinic-kubeconfig.json'
[IO.File]::WriteAllText($kubeconfigPath, ($scopedConfig | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding($false)))
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $kubeconfigPath /inheritance:r /grant:r "$($owner):(F)" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict access to the local Kubernetes credential file.' }

Write-Host "Created the namespace-scoped Jenkins ServiceAccount credential at $kubeconfigPath"
