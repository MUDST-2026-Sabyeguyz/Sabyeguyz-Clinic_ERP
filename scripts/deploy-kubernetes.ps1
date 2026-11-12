param(
    [string]$Namespace = 'clinic-present',
    [string]$ImageTag,
    [switch]$SkipBuild,
    # Loads demo accounts with publicly known passwords. Never use outside a throwaway demo cluster.
    [switch]$WithDemoData
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoPath
if ($Namespace -notmatch '^clinic-[a-z0-9-]+$') { throw 'Use a dedicated clinic-* demo namespace.' }
foreach ($tool in @('git', 'docker', 'kubectl')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required on the Windows agent." }
}
function Run-Kube {
    & kubectl --context docker-desktop -n $Namespace @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($args -join ' ')" }
}
function New-DemoSecret {
    $bytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
& kubectl --context docker-desktop get nodes
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop Kubernetes before deploying.' }
if (-not $ImageTag) {
    $sha = (& git rev-parse --short=12 HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source revision.' }
    $ImageTag = "$sha-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))"
}
if ($ImageTag -notmatch '^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$') { throw 'Invalid image tag.' }
$backendImage = "docker.io/library/clinic-backend:$ImageTag"
$frontendImage = "docker.io/library/clinic-frontend:$ImageTag"
if (-not $SkipBuild) {
    & docker build -t $backendImage ./backend
    if ($LASTEXITCODE -ne 0) { throw 'Backend image build failed.' }
    & docker build -t $frontendImage ./frontend
    if ($LASTEXITCODE -ne 0) { throw 'Frontend image build failed.' }
}

# Visible kind nodes can import the images explicitly. Otherwise Docker Desktop's
# containerd registry mirror supplies the local images to its Kubernetes nodes.
$nodes = (& kubectl --context docker-desktop get nodes -o json | ConvertFrom-Json).items.metadata.name
if ($LASTEXITCODE -ne 0) { throw 'Cannot list Kubernetes nodes.' }
$visibleNodes = @(& docker ps --filter 'label=io.x-k8s.kind.cluster' --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Docker is not available.' }
$targetNodes = @($visibleNodes | Where-Object { $_ -in $nodes })
if ($targetNodes.Count -gt 0) {
    if ($targetNodes.Count -ne @($nodes).Count) { throw 'Only some cluster nodes are visible to Docker. Enable Show system containers in Docker Desktop.' }
    $archive = Join-Path ([IO.Path]::GetTempPath()) ('clinic-images-' + [guid]::NewGuid().ToString('N') + '.tar')
    $remoteArchive = '/tmp/' + [IO.Path]::GetFileName($archive)
    try {
        & docker image save --output $archive $backendImage $frontendImage
        if ($LASTEXITCODE -ne 0) { throw 'Image export failed.' }
        foreach ($node in $targetNodes) {
            & docker cp $archive "${node}:$remoteArchive"
            if ($LASTEXITCODE -ne 0) { throw "Image copy failed: $node" }
            try {
                & docker exec $node ctr --namespace k8s.io images import $remoteArchive
                if ($LASTEXITCODE -ne 0) { throw "Image import failed: $node" }
            } finally { & docker exec $node rm -f $remoteArchive }
        }
    } finally { if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive } }
}

$existingNamespace = & kubectl --context docker-desktop get namespace $Namespace --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the deployment namespace.' }
if (-not $existingNamespace) {
    & kubectl --context docker-desktop create namespace $Namespace
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create the deployment namespace.' }
}
$secret = Run-Kube get secret clinic-secrets --ignore-not-found -o name
if (-not $secret) {
    $password = New-DemoSecret
    $secretFile = Join-Path ([IO.Path]::GetTempPath()) ('clinic-secret-' + [guid]::NewGuid().ToString('N') + '.env')
    try {
        [IO.File]::WriteAllLines($secretFile, @("POSTGRES_PASSWORD=$password", "SPRING_DATASOURCE_PASSWORD=$password", "JWT_SECRET=$(New-DemoSecret)"), (New-Object Text.UTF8Encoding($false)))
        Run-Kube create secret generic clinic-secrets "--from-env-file=$secretFile"
    } finally { if (Test-Path -LiteralPath $secretFile) { Remove-Item -LiteralPath $secretFile } }
}
$manifest = Join-Path ([IO.Path]::GetTempPath()) ('clinic-deploy-' + [guid]::NewGuid().ToString('N') + '.yaml')
try {
    $yaml = [IO.File]::ReadAllText((Join-Path $repoPath 'deploy/kubernetes/app.yaml'))
    $yaml = $yaml.Replace('__BACKEND_IMAGE__', $backendImage).Replace('__FRONTEND_IMAGE__', $frontendImage).Replace('__FLYWAY_EXTRA_LOCATIONS__', $(if ($WithDemoData) { ',classpath:db/seed_v2' } else { '' }))
    [IO.File]::WriteAllText($manifest, $yaml, (New-Object Text.UTF8Encoding($false)))
    Run-Kube apply -f $manifest
    Run-Kube rollout status statefulset/postgres --timeout=180s
    Run-Kube rollout status deployment/backend --timeout=360s
    Run-Kube rollout status deployment/frontend --timeout=180s
    Run-Kube get 'pods,services,pvc' -o wide
    Write-Host "DEPLOYED image tag: $ImageTag"
    Write-Host "Open the demo: kubectl --context docker-desktop -n $Namespace port-forward service/frontend 38001:80"
} catch {
    & kubectl --context docker-desktop -n $Namespace get pods -o wide
    & kubectl --context docker-desktop -n $Namespace get events --sort-by=.metadata.creationTimestamp
    & kubectl --context docker-desktop -n $Namespace logs deployment/backend --tail=80
    throw
} finally { if (Test-Path -LiteralPath $manifest) { Remove-Item -LiteralPath $manifest } }
