param([string]$Context = 'docker-desktop', [int]$Port = 18089, [switch]$TestPersistence)
. "$PSScriptRoot/Common.ps1"
Assert-LocalContext $Context
Initialize-PrivateRuntime
$checks = [System.Collections.Generic.List[string]]::new()
$forward = $null
$base = "http://127.0.0.1:$Port"
function Assert-Check([bool]$Condition, [string]$Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $checks.Add($Name)
    Write-Host "PASS: $Name"
}
try {
    $start = @{FilePath = (Get-Command kubectl).Source; ArgumentList = @('--context', $Context, '-n', 'clinic-local', 'port-forward', '--address=127.0.0.1', 'service/clinic-ingress', "${Port}:80"); PassThru = $true; RedirectStandardOutput = (Join-Path $RuntimeRoot 'test-forward.log'); RedirectStandardError = (Join-Path $RuntimeRoot 'test-forward-error.log')}
    if ($env:OS -eq 'Windows_NT') { $start.WindowStyle = 'Hidden' }
    $forward = Start-Process @start
    $ready = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        if ($forward.HasExited) { throw 'Port forward exited. Check test-forward-error.log (the port may be busy).' }
        try { $health = Invoke-RestMethod "$base/actuator/health" -TimeoutSec 3; if ($health.status -eq 'UP') { $ready = $true; break } } catch {}
        Start-Sleep -Seconds 2
    }
    Assert-Check $ready 'Ingress reaches backend and PostgreSQL health'
    $page = Invoke-WebRequest "$base/" -UseBasicParsing
    Assert-Check ($page.StatusCode -eq 200 -and $page.Content -match '<div id="app">') 'Ingress serves production Vue app'
    $deep = Invoke-WebRequest "$base/staff/patients" -UseBasicParsing
    Assert-Check ($deep.StatusCode -eq 200 -and $deep.Content -match '<div id="app">') 'Vue route refresh fallback'
    $unauthorized = 0
    try { Invoke-RestMethod "$base/api/patients" | Out-Null } catch { $unauthorized = [int]$_.Exception.Response.StatusCode }
    Assert-Check ($unauthorized -eq 401) 'Protected API rejects anonymous requests'
    $credentials = Get-Content (Join-Path $RuntimeRoot "$Context-credentials.json") -Raw | ConvertFrom-Json
    $login = @{ username = $credentials.MANAGER_USERNAME; password = $credentials.MANAGER_PASSWORD } | ConvertTo-Json
    $session = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $login
    Assert-Check ($session.role -eq 'MANAGER' -and !!$session.token) 'Real manager login through Ingress'
    $headers = @{ Authorization = "Bearer $($session.token)" }
    $branches = @(Invoke-RestMethod "$base/api/branches" -Headers $headers)
    if (!($branches | Where-Object branchCode -eq '99')) {
        $branch = @{ branchCode = '99'; name = 'Kubernetes Synthetic Demo'; address = 'Local test data only'; phone = '020000099' } | ConvertTo-Json
        Invoke-RestMethod "$base/api/branches" -Headers $headers -Method Post -ContentType 'application/json' -Body $branch | Out-Null
    }
    $marker = 'K8S' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $patientBody = @{ branchCode = '99'; customerType = 'Foreigner'; title = 'Mr'; firstName = $marker; lastName = 'Synthetic'; gender = 'Male'; identityDocumentType = 'PASSPORT'; identityNumber = $marker; birthDate = '1990-01-01'; nationality = 'Test'; address = 'Local Kubernetes acceptance' } | ConvertTo-Json
    $patient = Invoke-RestMethod "$base/api/patients" -Method Post -Headers $headers -ContentType 'application/json' -Body $patientBody
    $readback = Invoke-RestMethod "$base/api/patients/$($patient.id)" -Headers $headers
    Assert-Check ($readback.hn -eq $patient.hn -and $readback.fullName -match $marker) 'Authenticated patient create and readback'
    if ($TestPersistence) {
        Invoke-Kube -n clinic-local rollout restart deployment/postgres | Out-Host
        Invoke-Kube -n clinic-local rollout status deployment/postgres --timeout=180s | Out-Host
        $survived = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            try { $restored = Invoke-RestMethod "$base/api/patients/$($patient.id)" -Headers $headers -TimeoutSec 5; $survived = $restored.hn -eq $patient.hn; if ($survived) { break } } catch {}
            Start-Sleep -Seconds 2
        }
        Assert-Check $survived 'PVC preserves patient after PostgreSQL pod replacement'
        Invoke-Kube -n clinic-local rollout restart deployment/backend | Out-Host
        Invoke-Kube -n clinic-local rollout status deployment/backend --timeout=360s | Out-Host
        $afterRestart = Invoke-RestMethod "$base/api/patients/$($patient.id)" -Headers $headers
        Assert-Check ($afterRestart.hn -eq $patient.hn) 'Backend rollout retains patient and valid JWT'
    }
    $pods = Invoke-Kube -n clinic-local get pods -o json | ConvertFrom-Json
    $pvc = Invoke-Kube -n clinic-local get pvc postgres-data -o json | ConvertFrom-Json
    Assert-Check ($pvc.status.phase -eq 'Bound') 'PersistentVolumeClaim is Bound'
    foreach ($pod in $pods.items | Where-Object { !$_.metadata.deletionTimestamp }) {
        Assert-Check ([bool]($pod.status.conditions | Where-Object { $_.type -eq 'Ready' -and $_.status -eq 'True' })) "Pod Ready: $($pod.metadata.labels.app)"
    }
    $release = Get-Content (Join-Path $RuntimeRoot 'release.json') -Raw | ConvertFrom-Json
    @{ result = 'PASS'; testedAt = (Get-Date).ToUniversalTime().ToString('o'); checks = $checks.ToArray(); release = $release; pods = @($pods.items | ForEach-Object { @{name = $_.metadata.name; images = @($_.status.containerStatuses | Select-Object image, imageID, ready, restartCount)} }) } |
        ConvertTo-Json -Depth 12 | Set-Content (Join-Path $RuntimeRoot 'acceptance.json') -Encoding utf8
} finally {
    if ($forward -and !$forward.HasExited) { Stop-Process -Id $forward.Id }
    $session = $null; $headers = $null; $credentials = $null; $login = $null
}
