param(
    [Alias('Context')][string]$KubeContext = 'docker-desktop',
    [string]$Namespace = 'clinic-present',
    [string]$DatabaseName = 'clinic_demo',
    [ValidateSet('none','minimal','full')][string]$SeedMode = 'none',
    [ValidateSet('unspecified','local-demo')][string]$DatabaseKind = 'unspecified',
    [ValidateSet('Auto','Kind','Desktop')][string]$ImageTransport = 'Auto',
    [string]$ImageTag,
    [switch]$SkipBuild,
    [switch]$SkipScan,
    [switch]$PreflightOnly
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoPath
if ($Namespace -notmatch '^clinic-[a-z0-9-]+$' -or $Namespace.Length -gt 63) { throw 'Use a dedicated clinic-* namespace, up to 63 characters.' }
if ($DatabaseName -notmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'DatabaseName must be a lowercase PostgreSQL identifier.' }
if ($SeedMode -ne 'none') {
    if ($DatabaseKind -ne 'local-demo') { throw 'Demo seed requires -DatabaseKind local-demo.' }
}
foreach ($tool in @('git','docker','kubectl')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required on the agent." }
}
function Run-Kube {
    & kubectl --context $KubeContext -n $Namespace @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($args -join ' ')" }
}
function Sql-Value([string]$Database, [string]$Sql) {
    $lines = Run-Kube exec postgres-0 -- psql -X -v ON_ERROR_STOP=1 -U clinic -d $Database -tAc $Sql
    return ([string]($lines -join [Environment]::NewLine)).Trim()
}
function Write-Utf8([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path,$Value,(New-Object Text.UTF8Encoding($false)))
}
function New-DemoSecret {
    $bytes=New-Object byte[] 48
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$clusterServer=(& kubectl config view --minify --context $KubeContext -o 'jsonpath={.clusters[0].cluster.server}').Trim()
if ($LASTEXITCODE -ne 0 -or -not $clusterServer) { throw 'Cannot identify the selected Kubernetes API server.' }
$clusterUri=[Uri]$clusterServer
$clusterHost=$clusterUri.Host.ToLowerInvariant()
$isLocalCluster=$clusterHost -in @('localhost','127.0.0.1','::1','kubernetes.docker.internal')
$isDockerDesktop=$clusterHost -eq 'kubernetes.docker.internal'
if (-not $isLocalCluster) { throw 'This local deployment script refuses non-local Kubernetes API servers.' }
if ($SeedMode -ne 'none' -and -not $isLocalCluster) { throw 'Demo seed is allowed only on a verified local Kubernetes context.' }
$nodes=Run-Kube get nodes -o json | ConvertFrom-Json
if (@($nodes.items).Count -eq 0) { throw 'The selected cluster has no nodes.' }
foreach ($node in $nodes.items) {
    if (-not @($node.status.conditions | Where-Object {$_.type -eq 'Ready' -and $_.status -eq 'True'}).Count) { throw "Node is not Ready: $($node.metadata.name)" }
}
$visibleNodes=@(& docker ps --filter 'label=io.x-k8s.kind.cluster' --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Docker is not available for the agent user.' }
$nodeNames=@($nodes.items | ForEach-Object {$_.metadata.name})
$targetNodes=@($visibleNodes | Where-Object {$_ -in $nodeNames})
if ($ImageTransport -eq 'Auto') {
    if ($targetNodes.Count -eq $nodeNames.Count) { $ImageTransport='Kind' }
    elseif ($isDockerDesktop) { $ImageTransport='Desktop' }
    else { throw 'No image loader found. Use visible kind nodes or the docker-desktop context.' }
}
if ($ImageTransport -eq 'Kind' -and $targetNodes.Count -ne $nodeNames.Count) { throw 'All kind node containers must be visible to Docker.' }
if ($ImageTransport -eq 'Desktop' -and -not $isDockerDesktop) { throw 'Desktop image transport requires a Docker Desktop Kubernetes API server.' }
$storage=Run-Kube get storageclass -o json | ConvertFrom-Json
if (-not @($storage.items | Where-Object {$_.metadata.annotations.'storageclass.kubernetes.io/is-default-class' -eq 'true' -or $_.metadata.annotations.'storageclass.beta.kubernetes.io/is-default-class' -eq 'true'}).Count) { throw 'No default StorageClass is available for PostgreSQL.' }
Write-Host "PREFLIGHT_OK context=$KubeContext namespace=$Namespace database=$DatabaseName databaseKind=$DatabaseKind seed=$SeedMode imageTransport=$ImageTransport"
if ($PreflightOnly) { return }
$sourceSha=([string](& git rev-parse HEAD)).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source revision.' }
$sourceDirty=-not [string]::IsNullOrWhiteSpace((& git status --porcelain | Out-String))
if (-not $ImageTag) { $ImageTag="$($sourceSha.Substring(0,12))-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))" }
if ($ImageTag -notmatch '^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$') { throw 'Invalid image tag.' }
$backendImage="docker.io/library/clinic-backend:$ImageTag"
$frontendImage="docker.io/library/clinic-frontend:$ImageTag"
if (-not $SkipBuild) {
    & docker build -t $backendImage ./backend
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    & docker build -t $frontendImage ./frontend
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
}
if (-not $SkipScan) {
    & (Join-Path $PSScriptRoot 'scan-kubernetes-images.ps1') -ImageTag $ImageTag
    if ($LASTEXITCODE -ne 0) { throw 'Image security scan failed; deployment stopped.' }
}
foreach ($image in @($backendImage,$frontendImage)) {
    & docker image inspect $image --format '{{.Id}}'
    if ($LASTEXITCODE -ne 0) { throw "Image is missing: $image" }
}
if ($ImageTransport -eq 'Kind') {
    & docker pull postgres:16-alpine
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL image pull failed.' }
    $archive=Join-Path ([IO.Path]::GetTempPath()) ('clinic-images-'+[guid]::NewGuid().ToString('N')+'.tar')
    $remoteArchive='/tmp/'+[IO.Path]::GetFileName($archive)
    try {
        & docker image save --output $archive $backendImage $frontendImage postgres:16-alpine
        if ($LASTEXITCODE -ne 0) { throw 'Image export failed.' }
        foreach ($node in $targetNodes) {
            $destination=$node+':'+$remoteArchive
            & docker cp $archive $destination
            if ($LASTEXITCODE -ne 0) { throw "Image copy failed: $node" }
            try {
                & docker exec $node ctr --namespace k8s.io images import $remoteArchive
                if ($LASTEXITCODE -ne 0) { throw "Image import failed: $node" }
            } finally { & docker exec $node rm -f $remoteArchive | Out-Null }
        }
    } finally { if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive } }
}
$existingNamespace=& kubectl --context $KubeContext get namespace $Namespace --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the namespace.' }
if (-not $existingNamespace) {
    $canCreateNamespace=(& kubectl --context $KubeContext auth can-i create namespaces).Trim()
    if ($LASTEXITCODE -ne 0 -or $canCreateNamespace -ne 'yes') { throw "Namespace $Namespace does not exist; create it with an administrator before deploying." }
    Run-Kube create namespace $Namespace
}
$secret=Run-Kube get secret clinic-secrets --ignore-not-found -o json
if (-not $secret) {
    $password=New-DemoSecret
    $secretFile=Join-Path ([IO.Path]::GetTempPath()) ('clinic-secret-'+[guid]::NewGuid().ToString('N')+'.env')
    try {
        $secretLines=@("POSTGRES_PASSWORD=$password","SPRING_DATASOURCE_PASSWORD=$password","JWT_SECRET=$(New-DemoSecret)")
        Write-Utf8 $secretFile ($secretLines -join [Environment]::NewLine)
        Run-Kube create secret generic clinic-secrets "--from-env-file=$secretFile"
    } finally { if (Test-Path -LiteralPath $secretFile) { Remove-Item -LiteralPath $secretFile } }
} else {
    $secretData=($secret | ConvertFrom-Json).data
    foreach ($key in @('POSTGRES_PASSWORD','SPRING_DATASOURCE_PASSWORD','JWT_SECRET')) {
        if (-not $secretData.$key) { throw "Existing clinic-secrets is missing $key; it was not overwritten." }
    }
    if ($secretData.POSTGRES_PASSWORD -ne $secretData.SPRING_DATASOURCE_PASSWORD) { throw 'PostgreSQL/backend passwords differ; secret was not overwritten.' }
}
$manifest=Join-Path ([IO.Path]::GetTempPath()) ('clinic-deploy-'+[guid]::NewGuid().ToString('N')+'.yaml')
$jobManifest=Join-Path ([IO.Path]::GetTempPath()) ('clinic-job-'+[guid]::NewGuid().ToString('N')+'.yaml')
$jobName='clinic-migrate-'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
try {
    Run-Kube apply -f (Join-Path $repoPath 'deploy/kubernetes/postgres.yaml')
    Run-Kube rollout status statefulset/postgres --timeout=180s
    if (-not (Sql-Value postgres "SELECT 1 FROM pg_database WHERE datname = '$DatabaseName'")) { Run-Kube exec postgres-0 -- createdb -U clinic $DatabaseName }
    $values=@{'__BACKEND_IMAGE__'=$backendImage;'__FRONTEND_IMAGE__'=$frontendImage;'__DATABASE_NAME__'=$DatabaseName;'__DATABASE_KIND__'=$DatabaseKind;'__SPRING_PROFILES__'='docker,v2';'__SEED_MODE__'=$SeedMode;'__MIGRATION_JOB__'=$jobName}
    $jobYaml=[IO.File]::ReadAllText((Join-Path $repoPath 'deploy/kubernetes/migration-job.yaml'))
    $appYaml=[IO.File]::ReadAllText((Join-Path $repoPath 'deploy/kubernetes/app.yaml'))
    foreach ($key in $values.Keys) { $jobYaml=$jobYaml.Replace($key,$values[$key]);$appYaml=$appYaml.Replace($key,$values[$key]) }
    Write-Utf8 $jobManifest $jobYaml
    Run-Kube apply -f $jobManifest
    $deadline=(Get-Date).AddMinutes(6)
    do {
        $job=Run-Kube get job $jobName -o json | ConvertFrom-Json
        if ($job.status.failed -gt 0) { throw 'Migration job failed. Application images were not rolled out.' }
        if ($job.status.succeeded -gt 0) { break }
        $jobPods=Run-Kube get pods -l "job-name=$jobName" -o json | ConvertFrom-Json
        foreach ($pod in $jobPods.items) {
            foreach ($container in $pod.status.containerStatuses) {
                if ($container.state.waiting.reason -in @('ErrImagePull','ImagePullBackOff','InvalidImageName')) { throw 'Node cannot load the image. Enable Show system containers and select Kind image transport.' }
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if (-not ($job.status.succeeded -gt 0)) { throw 'Migration job timed out.' }
    $migrationLog=Run-Kube logs "job/$jobName" | Out-String
    Write-Host $migrationLog
    if ($migrationLog -notmatch 'CLINIC_MIGRATION_OK') { throw 'Migration job did not confirm completion.' }
    Write-Utf8 $manifest $appYaml
    Run-Kube apply -f $manifest
    Run-Kube rollout status deployment/backend --timeout=360s
    Run-Kube rollout status deployment/frontend --timeout=180s
    $deployments=Run-Kube get deployments backend frontend -o json | ConvertFrom-Json
    foreach ($deployment in $deployments.items) {
        $expected=if ($deployment.metadata.name -eq 'backend') {$backendImage} else {$frontendImage}
        if ($deployment.spec.template.spec.containers[0].image -ne $expected -or $deployment.status.availableReplicas -lt $deployment.spec.replicas) { throw "Deployment image/replicas do not match: $($deployment.metadata.name)" }
    }
    if ((Sql-Value $DatabaseName 'SELECT count(*) FROM flyway_schema_history WHERE NOT success') -ne '0') { throw 'Database contains failed migrations.' }
    $patientCount=Sql-Value $DatabaseName 'SELECT count(*) FROM patient'
    $smoke = & (Join-Path $PSScriptRoot 'smoke-kubernetes.ps1') -KubeContext $KubeContext -Namespace $Namespace
    $pods=Run-Kube get pods -o json | ConvertFrom-Json
    $evidence=[ordered]@{sourceSha=$sourceSha;sourceDirty=$sourceDirty;context=$KubeContext;namespace=$Namespace;database=$DatabaseName;seedMode=$SeedMode;imageTag=$ImageTag;migrationJob=$jobName;migrationCompleted=$true;patientCount=[int]$patientCount;smoke=$smoke;completedAtUtc=[DateTime]::UtcNow.ToString('o');imageIDs=@($pods.items.status.containerStatuses | ForEach-Object {$_.imageID})}
    Write-Utf8 (Join-Path $repoPath 'deployment-evidence.json') ($evidence | ConvertTo-Json -Depth 5)
    Run-Kube get pods,services,pvc -o wide
    Write-Host "DEPLOYED source=$sourceSha dirty=$sourceDirty database=$DatabaseName patients=$patientCount"
    Write-Host "Open: kubectl --context $KubeContext -n $Namespace port-forward service/frontend 38001:80"
} catch {
    & kubectl --context $KubeContext -n $Namespace get pods -o wide
    & kubectl --context $KubeContext -n $Namespace get events --sort-by=.metadata.creationTimestamp
    & kubectl --context $KubeContext -n $Namespace logs "job/$jobName" --tail=80
    throw
} finally {
    foreach ($path in @($manifest,$jobManifest)) { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path } }
}
