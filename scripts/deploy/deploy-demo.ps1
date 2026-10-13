[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$')][string]$BackendImage,
    [Parameter(Mandatory)][ValidatePattern('^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$')][string]$FrontendImage,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$SourceSha,
    [ValidateSet('clinic-phase1')][string]$Context = 'clinic-phase1'
)
$ErrorActionPreference = 'Stop'
$namespace = 'clinic-erp-demo-20261017'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$evidenceDir = Join-Path $repoRoot 'evidence/k8s'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$minikubeExe = $env:MINIKUBE_EXE
if (-not $minikubeExe) { $minikubeExe = (Get-Command minikube -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $minikubeExe)) { throw 'Configure MINIKUBE_EXE with the installed Minikube path' }
if (-not $env:SMOKE_USERNAME -or -not $env:SMOKE_PASSWORD) { throw 'Set synthetic Manager smoke credentials' }
$env:SOURCE_SHA = $SourceSha

function Invoke-K {
    & kubectl --context $Context -n $namespace @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($args[0])" }
}
function Invoke-Tool([string]$Exe, [string[]]$ToolArgs) {
    & $Exe @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "Tool failed: $Exe" }
}

# This deployer is deliberately scoped to an isolated demonstration namespace.
$nsJson = Invoke-K get namespace $namespace --ignore-not-found -o json
if ($nsJson) {
    $ns = ($nsJson -join "`n") | ConvertFrom-Json
    if ($ns.metadata.labels.'app.kubernetes.io/part-of' -ne 'clinic-erp-presentation-demo') {
        throw 'Refusing an existing namespace without the presentation-demo ownership label'
    }
} else {
    $nsDoc = @{apiVersion='v1';kind='Namespace';metadata=@{name=$namespace;labels=@{'app.kubernetes.io/part-of'='clinic-erp-presentation-demo'}}}
    $nsDoc | ConvertTo-Json -Depth 8 | kubectl --context $Context apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Namespace creation failed' }
}
Invoke-K get storageclass standard | Out-Null
Invoke-K get ingressclass traefik | Out-Null

# Import exactly the digests already pulled by the CI job. Never let the cluster
# silently use a mutable tag or pull an untested application image.
foreach ($imageRef in @($BackendImage, $FrontendImage)) {
    $imageRepo = ($imageRef -split '@')[0]
    $sourceTag = "${imageRepo}:sha-$SourceSha"
    Invoke-Tool docker @('tag', $imageRef, $sourceTag)
    Invoke-Tool $minikubeExe @('-p', $Context, 'image', 'load', '--daemon=true', $sourceTag)
    Invoke-Tool $minikubeExe @('-p', $Context, 'ssh', '--', "sudo ctr -n k8s.io images tag --local --force $sourceTag $imageRef")
    $inventory = & $minikubeExe -p $Context ssh -- 'sudo ctr -n k8s.io images ls'
    if ($LASTEXITCODE -ne 0 -or -not (($inventory -join "`n").Contains($imageRef))) { throw 'Digest alias missing in Minikube containerd' }
}

if (-not (Invoke-K get secret clinic-runtime --ignore-not-found -o name)) {
    $secretDoc = @{
        apiVersion='v1';kind='Secret';metadata=@{name='clinic-runtime';namespace=$namespace}
        type='Opaque';stringData=@{
            POSTGRES_USER='clinic'
            POSTGRES_PASSWORD=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
            JWT_SECRET=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        }
    }
    $secretDoc | ConvertTo-Json -Depth 8 | kubectl --context $Context -n $namespace apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Demo secret provisioning failed' }
    $secretDoc = $null
}

$template = Get-Content -Raw (Join-Path $repoRoot 'deploy/k8s/clinic-demo.template.yaml')
$rendered = $template.Replace('__NAMESPACE__', $namespace).Replace('__SOURCE_SHA__', $SourceSha).Replace('__BACKEND_IMAGE__', $BackendImage).Replace('__FRONTEND_IMAGE__', $FrontendImage)
if ($rendered -match '__[A-Z_]+__') { throw 'Unresolved manifest placeholders' }
$manifestPath = Join-Path $repoRoot 'deploy/k8s/clinic-demo.rendered.yaml'
Set-Content -LiteralPath $manifestPath -Value $rendered -Encoding utf8
Invoke-K apply --dry-run=server -f $manifestPath | Out-Null

$oldBackend = Invoke-K get deployment clinic-demo-backend --ignore-not-found -o 'jsonpath={.spec.template.spec.containers[0].image}'
$oldFrontend = Invoke-K get deployment clinic-demo-frontend --ignore-not-found -o 'jsonpath={.spec.template.spec.containers[0].image}'
$schemaFingerprintSql = "SELECT coalesce(string_agg(coalesce(version,script) || ':' || coalesce(checksum::text,'') || ':' || success::text, ',' ORDER BY installed_rank),'') FROM flyway_schema_history"
$oldSchema = if ($oldBackend) { Invoke-K exec clinic-demo-postgres-0 -- psql -U clinic -d clinic_erp -Atc $schemaFingerprintSql } else { $null }
$forwarder = $null
try {
    Invoke-K apply -f $manifestPath
    Invoke-K rollout status statefulset/clinic-demo-postgres --timeout=240s
    Invoke-K rollout status deployment/clinic-demo-backend --timeout=300s
    Invoke-K rollout status deployment/clinic-demo-frontend --timeout=180s
    $actualBackend = Invoke-K get deployment clinic-demo-backend -o 'jsonpath={.spec.template.spec.containers[0].image}'
    $actualFrontend = Invoke-K get deployment clinic-demo-frontend -o 'jsonpath={.spec.template.spec.containers[0].image}'
    if ($actualBackend -ne $BackendImage -or $actualFrontend -ne $FrontendImage) { throw 'Deployed image references differ from CI digests' }

    $forwarder = Start-Process -FilePath (Get-Command kubectl).Source -ArgumentList @('--context',$Context,'-n','kube-system','port-forward','svc/traefik','17969:80','--address','127.0.0.1') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $evidenceDir 'port-forward.log') -RedirectStandardError (Join-Path $evidenceDir 'port-forward-error.log')
    $baseUrl = 'http://localhost:17969/sabyeguyz'
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        if ($forwarder.HasExited) { throw 'Ingress port-forward exited; check the evidence log' }
        try {
            $health = Invoke-WebRequest "$baseUrl/healthz" -TimeoutSec 5
            if ($health.StatusCode -eq 200) { $ready=$true; break }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw 'Ingress frontend health did not become ready' }
    $page = Invoke-WebRequest "$baseUrl/" -TimeoutSec 15
    if ($page.Content -notmatch '/sabyeguyz/assets/[^" ]+\.js') { throw 'HTML did not reference the expected prefixed JavaScript asset' }
    $assetPath = $Matches[0]
    $asset = Invoke-WebRequest "http://localhost:17969$assetPath" -TimeoutSec 15
    if ($asset.StatusCode -ne 200 -or $asset.Headers.'Content-Type' -notmatch 'javascript') { throw 'Ingress asset response was not JavaScript' }

    Invoke-Tool python @((Join-Path $repoRoot 'scripts/ci/api-smoke.py'), '--base-url', $baseUrl, '--output', (Join-Path $evidenceDir 'ingress-create.json'))
    $patient = Get-Content -Raw (Join-Path $evidenceDir 'ingress-create.json') | ConvertFrom-Json
    if ($patient.hn -notmatch '^[A-Za-z0-9-]+$') { throw 'Unexpected HN format in synthetic smoke output' }
    $sqlCount = "SELECT count(*) FROM patients WHERE hn='$($patient.hn)'"
    $dbCount = Invoke-K exec clinic-demo-postgres-0 -- psql -U clinic -d clinic_erp -Atc $sqlCount
    if ($dbCount -ne '1') { throw 'HTTP-created patient was not found exactly once in PostgreSQL' }
    Invoke-K exec clinic-demo-postgres-0 -- psql -U clinic -d clinic_erp -Atc 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank' | Set-Content (Join-Path $evidenceDir 'flyway-history.txt')

    # A restore rehearsal is a separate test from retaining the same PVC.
    Invoke-K exec clinic-demo-postgres-0 -- pg_dump -U clinic -d clinic_erp -Fc -f /tmp/clinic-demo.dump
    $rehearsalDb = 'demo_restore_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
    try {
        Invoke-K exec clinic-demo-postgres-0 -- createdb -U clinic $rehearsalDb
        Invoke-K exec clinic-demo-postgres-0 -- pg_restore -U clinic -d $rehearsalDb --exit-on-error --no-owner /tmp/clinic-demo.dump
        $restoredCount = Invoke-K exec clinic-demo-postgres-0 -- psql -U clinic -d $rehearsalDb -Atc $sqlCount
        if ($restoredCount -ne '1') { throw 'Restore rehearsal readback failed' }
    } finally {
        Invoke-K exec clinic-demo-postgres-0 -- dropdb -U clinic --if-exists $rehearsalDb
        Invoke-K exec clinic-demo-postgres-0 -- rm -f /tmp/clinic-demo.dump
    }
    $pvcBefore = Invoke-K get pvc data-clinic-demo-postgres-0 -o 'jsonpath={.metadata.uid}'
    Invoke-K delete pod clinic-demo-postgres-0 --wait=true
    Invoke-K rollout status statefulset/clinic-demo-postgres --timeout=240s
    Invoke-K rollout restart deployment/clinic-demo-backend
    Invoke-K rollout status deployment/clinic-demo-backend --timeout=300s
    $pvcAfter = Invoke-K get pvc data-clinic-demo-postgres-0 -o 'jsonpath={.metadata.uid}'
    if ($pvcBefore -ne $pvcAfter) { throw 'Database recreation did not retain the same PVC' }
    Invoke-Tool python @((Join-Path $repoRoot 'scripts/ci/api-smoke.py'), '--base-url', $baseUrl, '--verify-existing', (Join-Path $evidenceDir 'ingress-create.json'), '--output', (Join-Path $evidenceDir 'ingress-persistence.json'))
    $checkpoint = @{
        status='PASS';source_sha=$SourceSha;context=$Context;namespace=$namespace
        backend_image=$BackendImage;frontend_image=$FrontendImage;pvc_uid=$pvcAfter
        ingress_health=200;html=200;javascript=200;postgres_patient_count=1
        restore_rehearsal='PASS';pod_recreation_readback='PASS';completed_at_utc=[DateTime]::UtcNow.ToString('o')
        boundary='Infrastructure and synthetic patient smoke only; financial acceptance remains separate'
    }
    $checkpoint | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidenceDir 'deployment-checkpoint.json') -Encoding utf8
    Invoke-K get pods,deployments,statefulsets,services,ingress,pvc -o wide | Set-Content (Join-Path $evidenceDir 'resources.txt')
    Write-Output "PASS: $SourceSha deployed to $Context/$namespace with authenticated Ingress, restore rehearsal and PVC persistence"
} catch {
    $failureMessage = $_.Exception.Message
    Invoke-K get pods,events -o wide | Set-Content (Join-Path $evidenceDir 'failure-resources.txt')
    # Image rollback is safe only when Flyway history is unchanged.
    if ($oldBackend -and $oldFrontend) {
        $currentSchema = Invoke-K exec clinic-demo-postgres-0 -- psql -U clinic -d clinic_erp -Atc $schemaFingerprintSql
        if ($currentSchema -eq $oldSchema) {
            Invoke-K set image deployment/clinic-demo-backend "backend=$oldBackend"
            Invoke-K set image deployment/clinic-demo-frontend "frontend=$oldFrontend"
            Invoke-K rollout status deployment/clinic-demo-backend --timeout=300s
            Invoke-K rollout status deployment/clinic-demo-frontend --timeout=180s
        } else {
            'Schema history changed. Automatic image rollback skipped; preserve PVC and review schema compatibility.' | Set-Content (Join-Path $evidenceDir 'rollback-blocked.txt')
        }
    }
    throw $failureMessage
} finally {
    if ($forwarder -and -not $forwarder.HasExited) { Stop-Process -Id $forwarder.Id }
}
