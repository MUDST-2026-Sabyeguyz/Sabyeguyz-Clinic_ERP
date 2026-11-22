param(
    [string]$Context = 'docker-desktop',
    [string]$ImageTag = '',
    [switch]$SkipBuild,
    [switch]$SkipImageLoad,
    [switch]$HostBuild,
    [ValidateSet('default', 'host')][string]$BuildNetwork = 'default'
)
. "$PSScriptRoot/Common.ps1"
Assert-LocalContext $Context
Initialize-PrivateRuntime
$revision = (& git -C $RepoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot determine source revision.' }
$sourceDirty = [bool](& git -C $RepoRoot status --porcelain -- backend frontend)
if (!$ImageTag) { $ImageTag = $revision.Substring(0, 12) + '-' + (Get-Date -Format 'yyyyMMddHHmmss') }
if ($ImageTag -notmatch '^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$') { throw 'Invalid image tag.' }
$images = @("clinic-backend:$ImageTag", "clinic-frontend:$ImageTag", 'postgres:16-alpine', 'traefik:v3.7.15')
if (!$SkipBuild) {
    if ($HostBuild) {
        Initialize-MavenTool
        $buildRoot = Join-Path $RuntimeRoot "host-build-$ImageTag"
        if (Test-Path -LiteralPath $buildRoot) { throw 'Host build workspace already exists. Choose a new ImageTag to build from a fresh source copy.' }
        New-Item -ItemType Directory -Path "$buildRoot/backend", "$buildRoot/frontend" -Force | Out-Null
        Copy-Item "$RepoRoot/backend/pom.xml" "$buildRoot/backend/pom.xml"
        Copy-Item "$RepoRoot/backend/src" "$buildRoot/backend/src" -Recurse
        Invoke-Tool mvn @('--batch-mode', '--no-transfer-progress', '-f', "$buildRoot/backend/pom.xml", 'package', '-Dmaven.test.skip=true')
        Copy-Item "$buildRoot/backend/target/clinic-erp-backend-0.0.1-SNAPSHOT.jar" "$buildRoot/backend/app.jar"
        $frontendSource = Join-Path $buildRoot 'frontend-source'
        # Use a separate dependency tree so a rebuild cannot remove modules used by running tests.
        $frontendFiles = & git -C $RepoRoot -c core.quotepath=false ls-files -- frontend
        if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate frontend source files.' }
        foreach ($file in $frontendFiles) {
            if (!$file.StartsWith('frontend/')) { throw 'Unexpected frontend source path.' }
            $destination = Join-Path $frontendSource $file.Substring('frontend/'.Length)
            New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $RepoRoot $file) -Destination $destination
        }
        Push-Location $frontendSource
        try { Invoke-Tool npm @('ci'); Invoke-Tool npm @('run', 'build') } finally { Pop-Location }
        Copy-Item "$frontendSource/dist" "$buildRoot/frontend/dist" -Recurse
        Copy-Item "$RepoRoot/frontend/nginx.conf" "$buildRoot/frontend/nginx.conf"
        Invoke-Tool docker @('build', '--label', "org.opencontainers.image.revision=$revision", '-f', "$KubeRoot/backend.runtime.Dockerfile", '-t', $images[0], "$buildRoot/backend")
        Invoke-Tool docker @('build', '--label', "org.opencontainers.image.revision=$revision", '-f', "$KubeRoot/frontend.runtime.Dockerfile", '-t', $images[1], "$buildRoot/frontend")
    } else {
        Invoke-Tool docker @('build', '--network', $BuildNetwork, '--label', "org.opencontainers.image.revision=$revision", '-t', $images[0], (Join-Path $RepoRoot 'backend'))
        Invoke-Tool docker @('build', '--network', $BuildNetwork, '--label', "org.opencontainers.image.revision=$revision", '-t', $images[1], (Join-Path $RepoRoot 'frontend'))
    }
}
foreach ($image in $images[2..3]) { Invoke-Tool docker @('pull', $image) }
$applicationRevision = $revision
if ($SkipBuild) {
    $builtRevisions = @($images[0..1] | ForEach-Object {
        $label = & docker image inspect $_ --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
        if ($LASTEXITCODE -ne 0 -or $label -notmatch '^[a-f0-9]{40}$') { throw "Missing source revision label on $_. Build the release first." }
        $label.Trim()
    })
    if ($builtRevisions[0] -ne $builtRevisions[1]) { throw 'Backend and frontend image source revisions differ. Rebuild both images.' }
    $applicationRevision = $builtRevisions[0]
}
if (!$SkipImageLoad) {
    switch ($Context) {
        'docker-desktop' {
            # Docker Desktop's kind node has its own containerd image store.
            if ($PSVersionTable.PSVersion -lt [version]'7.4') { throw 'Image streaming requires PowerShell 7.4+ to preserve native binary pipelines.' }
            $nodes = @(Invoke-Kube get nodes -o 'jsonpath={.items[*].metadata.name}') -join ' '
            foreach ($node in $nodes.Trim().Split(' ', [StringSplitOptions]::RemoveEmptyEntries)) {
                Invoke-Tool docker @('inspect', $node, '--format', '{{.Name}}')
                $architecture = (Invoke-Kube get node $node -o 'jsonpath={.status.nodeInfo.architecture}').Trim()
                # Stream native bytes directly; copying a large tar through the Windows filesystem is slow.
                & docker image save --platform "linux/$architecture" @images |
                    & docker exec -i $node ctr -n k8s.io images import --no-unpack - | Out-Host
                if ($LASTEXITCODE -ne 0) { throw "Could not import release images into $node." }
            }
        }
        'minikube' { foreach ($image in $images) { Invoke-Tool minikube @('image', 'load', $image) } }
        'kind-clinic-ci' { Invoke-Tool kind (@('load', 'docker-image', '--name', 'clinic-ci') + $images) }
    }
}
Invoke-Kube apply -f (Join-Path $KubeRoot 'namespace.yaml') | Out-Host
$credentialFile = Join-Path $RuntimeRoot "$Context-credentials.json"
$existingSecret = & kubectl --context $Context -n clinic-local get secret clinic-secrets --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Cannot check existing Secret.' }
if (!$existingSecret) {
    if (Test-Path $credentialFile) { $credentials = Get-Content $credentialFile -Raw | ConvertFrom-Json }
    else {
        $credentials = [ordered]@{ POSTGRES_PASSWORD = New-RandomSecret; JWT_SECRET = New-RandomSecret; MANAGER_USERNAME = 'clinic-admin'; MANAGER_PASSWORD = New-RandomSecret }
        $credentials | ConvertTo-Json | Set-Content -LiteralPath $credentialFile -Encoding utf8
    }
    $secret = @{ apiVersion = 'v1'; kind = 'Secret'; metadata = @{name = 'clinic-secrets'; namespace = 'clinic-local'}; type = 'Opaque'; stringData = $credentials }
    $secret | ConvertTo-Json -Depth 8 | & kubectl --context $Context create -f - | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create application Secret.' }
} elseif (!(Test-Path $credentialFile)) {
    throw 'Existing Secret has no matching local credentials file. Keep the Secret and restore your private credentials file; do not rotate the DB password automatically.'
}
$overlay = Join-Path $RuntimeRoot 'release'
$dirtyAnnotation = if ($SkipBuild) { 'unknown-reused-images' } else { "$sourceDirty" }
New-Item -ItemType Directory -Force -Path $overlay | Out-Null
Invoke-Kube kustomize $KubeRoot | Set-Content (Join-Path $overlay 'base.yaml') -Encoding utf8
@"
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - base.yaml
commonAnnotations:
  clinic.sabyeguyz/source-revision: '$applicationRevision'
  clinic.sabyeguyz/deployment-source-revision: '$revision'
  clinic.sabyeguyz/application-source-dirty: '$dirtyAnnotation'
images:
  - name: clinic-backend
    newTag: $ImageTag
  - name: clinic-frontend
    newTag: $ImageTag
"@ | Set-Content (Join-Path $overlay 'kustomization.yaml') -Encoding utf8
Invoke-Kube apply -k $overlay | Out-Host
foreach ($deployment in @('postgres', 'backend', 'frontend', 'clinic-traefik')) {
    Invoke-Kube -n clinic-local rollout status "deployment/$deployment" --timeout=360s | Out-Host
}
$imageIds = @{}
foreach ($image in $images) { $imageIds[$image] = (& docker image inspect $image --format '{{.Id}}').Trim() }
@{ sourceRevision = $applicationRevision; deploymentSourceRevision = $revision; applicationSourceDirty = $(if ($SkipBuild) { $null } else { $sourceDirty }); hostBuild = $(if ($SkipBuild) { $null } else { [bool]$HostBuild }); reusedImages = [bool]$SkipBuild; imageTag = $ImageTag; images = $imageIds; context = $Context; deployedAt = (Get-Date).ToUniversalTime().ToString('o') } |
    ConvertTo-Json -Depth 5 | Set-Content (Join-Path $RuntimeRoot 'release.json') -Encoding utf8
Invoke-Kube -n clinic-local get 'pods,services,ingress,pvc' | Out-Host
Write-Host "Ready. Open: ./deploy/k8s/Open-Local.ps1 -Context $Context"
Write-Host "Login user: clinic-admin; read MANAGER_PASSWORD locally from $credentialFile"
