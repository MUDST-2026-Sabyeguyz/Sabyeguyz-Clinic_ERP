<#
.SYNOPSIS
  Runs the frontend Cypress E2E suites (Patients, Manager, Therapist) against an isolated, disposable stack.

.DESCRIPTION
  Stack:  Chrome (Cypress) -> Vite :5173 -> test backend :18080 -> PostgreSQL 16 :15434 (database patients_bcc)

  The suites call /__cypress/reset, which wipes the database before every test, so this script only ever uses its own
  containers (clinic-patients-bcc-pg, clinic-e2e-backend) and never touches your normal database, Neon or compose stack.

  The test backend runs inside Docker by default because the JVM on some Windows machines cannot start it
  ("Unable to establish loopback connection"). Use -HostBackend to run it with the local Maven/JDK instead.

.PARAMETER Suite
  all (default) | patients | manager | therapist. Suites always run one after another (they share one database).

.PARAMETER Spec
  Optional spec file to run inside a single suite, e.g. cypress/e2e-therapist/services.cy.js (needs -Suite).

.PARAMETER Headed
  Show the Chrome window so you can watch the steps.

.PARAMETER KeepRunning
  Leave the containers and Vite running afterwards (faster reruns). Default: everything started here is stopped.

.PARAMETER HostBackend
  Start the backend with the local `mvn -Pcypress-v2 spring-boot:start` instead of Docker.

.EXAMPLE
  .\scripts\run-frontend-e2e.ps1
  .\scripts\run-frontend-e2e.ps1 -Suite therapist -Headed
  .\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/services.cy.js -KeepRunning
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'patients', 'manager', 'therapist')]
    [string]$Suite = 'all',
    [string]$Spec = '',
    [switch]$Headed,
    [switch]$KeepRunning,
    [switch]$HostBackend
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$Frontend = Join-Path $Root 'frontend'
$Backend = Join-Path $Root 'backend'

# Everything below is fixed on purpose: a wrong target here would let the reset wipe a real database.
$PgName = 'clinic-patients-bcc-pg'
$BackendName = 'clinic-e2e-backend'
$NetworkName = 'clinic-e2e-net'
$M2Volume = 'clinic-e2e-m2'
$PgPort = 15434
$ApiPort = 18080
$ViteUrl = 'http://localhost:5173'
$ApiUrl = "http://127.0.0.1:$ApiPort"
$WorkCopy = Join-Path $env:TEMP 'clinic-e2e-backend'

$startedPg = $false
$startedBackend = $false
$viteProcess = $null
$failed = $false

function Step([string]$text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

function Test-Http([string]$url) {
    try { return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { return $false }
}

# Runs a native command without letting its stderr/exit code abort the script; returns the exit code.
function Quiet([scriptblock]$command) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $command *> $null } finally { $ErrorActionPreference = $previous }
    return $LASTEXITCODE
}

function Wait-Until([scriptblock]$check, [int]$seconds, [string]$what) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (& $check) { return }
        Start-Sleep -Seconds 3
    }
    throw "Timed out after $seconds s waiting for $what."
}

function Test-Container([string]$name) {
    return [bool](docker ps -a --filter "name=^/$name$" --format '{{.Names}}')
}

function Test-ContainerRunning([string]$name) {
    return [bool](docker ps --filter "name=^/$name$" --format '{{.Names}}')
}

function Test-PortBusy([int]$port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}

function Stop-Stack {
    if ($viteProcess -and -not $viteProcess.HasExited) {
        Step 'Stopping Vite'
        taskkill /PID $viteProcess.Id /T /F | Out-Null
    }
    if ($startedBackend) {
        Step 'Removing the test backend container'
        docker rm -f $BackendName | Out-Null
        if (Test-Path $WorkCopy) { Remove-Item -Recurse -Force $WorkCopy }
    }
    if ($startedPg) {
        Step 'Removing the disposable database container'
        docker rm -f $PgName | Out-Null
    }
    if ($startedBackend -or $startedPg) {
        Quiet { docker network rm $NetworkName } | Out-Null
    }
}

try {
    if ($Spec -and $Suite -eq 'all') { throw '-Spec needs a single suite: add -Suite patients|manager|therapist.' }

    Step 'Checking prerequisites'
    foreach ($tool in 'docker', 'node', 'npx') {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool was not found in PATH." }
    }
    if ((Quiet { docker info }) -ne 0) { throw 'Docker engine is not running. Start Docker Desktop and wait for "Engine running".' }
    if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
        Step 'Installing frontend dependencies (npm ci)'
        Push-Location $Frontend; npm ci --no-audit --no-fund; Pop-Location
    }

    # ---- PostgreSQL (disposable) ----
    Step "PostgreSQL 16 on 127.0.0.1:$PgPort (database patients_bcc)"
    if ((Quiet { docker network inspect $NetworkName }) -ne 0) { docker network create $NetworkName | Out-Null }
    if (Test-ContainerRunning $PgName) {
        Write-Host 'Reusing the running container.'
    } else {
        if (Test-Container $PgName) { docker rm -f $PgName | Out-Null }
        if (Test-PortBusy $PgPort) { throw "Port $PgPort is used by something else. Free it first." }
        docker run -d --name $PgName --network $NetworkName -e POSTGRES_DB=patients_bcc -e POSTGRES_USER=clinic `
            -e POSTGRES_PASSWORD=clinic -p "127.0.0.1:${PgPort}:5432" postgres:16 | Out-Null
        $startedPg = $true
    }
    Quiet { docker network connect $NetworkName $PgName } | Out-Null
    Wait-Until { (Quiet { docker exec $PgName pg_isready -U clinic -d patients_bcc }) -eq 0 } 90 'PostgreSQL'

    # ---- Test backend ----
    Step "Test backend (profile cypress-v2) on $ApiUrl"
    if (Test-Http "$ApiUrl/actuator/health") {
        Write-Host 'A backend already answers on this port - reusing it.'
    } elseif ($HostBackend) {
        $env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:$PgPort/patients_bcc"
        $env:DATABASE_USERNAME = 'clinic'; $env:DATABASE_PASSWORD = 'clinic'
        Push-Location $Backend
        mvn --batch-mode --no-transfer-progress -Pcypress-v2 test-compile spring-boot:start
        Pop-Location
        if ($LASTEXITCODE -ne 0) { throw 'Host backend failed to start. Retry without -HostBackend to use Docker.' }
    } else {
        if (Test-PortBusy $ApiPort) { throw "Port $ApiPort is used by something else. Free it first." }
        if (Test-Container $BackendName) { docker rm -f $BackendName | Out-Null }
        # Work on a copy so the build never touches backend/target.
        if (Test-Path $WorkCopy) { Remove-Item -Recurse -Force $WorkCopy }
        New-Item -ItemType Directory -Path $WorkCopy | Out-Null
        Copy-Item (Join-Path $Backend 'pom.xml') $WorkCopy
        Copy-Item (Join-Path $Backend 'src') $WorkCopy -Recurse
        docker run -d --name $BackendName --network $NetworkName -p "127.0.0.1:${ApiPort}:${ApiPort}" `
            -v "$($WorkCopy.Replace([char]92,[char]47)):/w" -v "${M2Volume}:/root/.m2" -w /w `
            -e "DATABASE_URL=jdbc:postgresql://${PgName}:5432/patients_bcc" -e DATABASE_USERNAME=clinic `
            -e DATABASE_PASSWORD=clinic -e SERVER_ADDRESS=0.0.0.0 `
            maven:3.9-eclipse-temurin-21 mvn -B --no-transfer-progress -Pcypress-v2 test-compile spring-boot:run | Out-Null
        $startedBackend = $true
        Write-Host 'Compiling and starting (first run downloads Maven dependencies, several minutes)...'
    }
    Wait-Until { Test-Http "$ApiUrl/actuator/health" } 900 'the test backend'

    # ---- Vite ----
    Step "Vite dev server on $ViteUrl (proxy /api -> $ApiUrl)"
    $env:VITE_API_PROXY_TARGET = $ApiUrl
    $env:CYPRESS_API_URL = $ApiUrl
    if (Test-Http $ViteUrl) {
        Write-Host 'Something already serves port 5173 - reusing it (it must proxy to the test backend).'
    } else {
        if (Test-PortBusy 5173) { throw 'Port 5173 is busy but not answering. Free it first.' }
        $viteProcess = Start-Process -FilePath 'npm.cmd' -WorkingDirectory $Frontend -PassThru -WindowStyle Hidden `
            -ArgumentList 'run', 'dev', '--', '--host', 'localhost', '--port', '5173', '--strictPort'
        Wait-Until { Test-Http $ViteUrl } 120 'Vite'
    }
    # Safety check: a login through Vite must reach the isolated backend (a wrong credential answers 401 from it).
    try {
        Invoke-WebRequest -Uri "$ViteUrl/api/auth/login" -Method Post -ContentType 'application/json' `
            -Body '{"username":"x","password":"y"}' -UseBasicParsing | Out-Null
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 401) { throw 'Vite does not reach the test backend (no 401 on /api/auth/login).' }
    }

    # ---- Cypress ----
    $suites = [ordered]@{
        patients  = @{ Config = 'cypress.patients.config.ts';  Reset = 'PATIENTS_BCC_RESET' }
        manager   = @{ Config = 'cypress.manager.config.ts';   Reset = 'MANAGER_E2E_RESET' }
        therapist = @{ Config = 'cypress.therapist.config.ts'; Reset = 'THERAPIST_E2E_RESET' }
    }
    $selected = if ($Suite -eq 'all') { $suites.Keys } else { @($Suite) }
    $results = @()
    Push-Location $Frontend
    foreach ($name in $selected) {
        Step "Cypress suite: $name"
        Remove-Item -Recurse -Force 'cypress/screenshots' -ErrorAction SilentlyContinue
        Remove-Item 'test-results/*.xml' -ErrorAction SilentlyContinue
        Set-Item -Path "Env:$($suites[$name].Reset)" -Value '1'
        $cypressArgs = @('cypress', 'run', '--config-file', $suites[$name].Config, '--browser', 'chrome')
        if ($Headed) { $cypressArgs += '--headed' }
        if ($Spec) { $cypressArgs += @('--spec', $Spec) }
        & npx.cmd @cypressArgs
        $exit = $LASTEXITCODE
        Remove-Item -Path "Env:$($suites[$name].Reset)"
        $tests = 0; $bad = 0
        $files = @(Get-ChildItem 'test-results' -Filter '*.xml' -ErrorAction SilentlyContinue)
        $listing = @()
        foreach ($file in $files) {
            $xml = [xml](Get-Content $file.FullName -Raw -Encoding UTF8)
            $tests += $xml.SelectNodes('//testcase').Count
            $bad += $xml.SelectNodes('//testcase[failure]').Count
            foreach ($case in $xml.SelectNodes('//testcase')) {
                $mark = if ($case.SelectSingleNode('failure')) { 'FAIL' } else { 'PASS' }
                $listing += ('  [{0}] {1}' -f $mark, $case.name)
            }
        }
        # Every test by name, so passing ones are visible too (Cypress prints only a summary with the JUnit reporter).
        Write-Host "`nTests in suite ${name}:" -ForegroundColor Cyan
        $listing | ForEach-Object { Write-Host $_ -ForegroundColor $(if ($_ -like '*[[]FAIL]*') { 'Red' } else { 'Green' }) }
        # Keep this suite's JUnit files in test-results/<suite>/ (the folder is git-ignored).
        $keep = Join-Path $Frontend "test-results/$name"
        Remove-Item -Recurse -Force $keep -ErrorAction SilentlyContinue
        if ($files.Count) { New-Item -ItemType Directory -Path $keep | Out-Null; $files | Move-Item -Destination $keep }
        $results += [pscustomobject]@{ Suite = $name; Tests = $tests; Passed = $tests - $bad; Failed = $bad; Exit = $exit }
        if ($exit -ne 0) { $failed = $true }
    }
    Pop-Location

    Step 'Summary'
    $results | Format-Table -AutoSize
    Write-Host "JUnit results: frontend\test-results\<suite>\*.xml   Screenshots of failures: frontend\cypress\screenshots"
}
finally {
    if ($KeepRunning) {
        Write-Host "`nLeaving the stack running (-KeepRunning). Stop it later with:" -ForegroundColor Yellow
        Write-Host "  docker rm -f $BackendName $PgName ; docker network rm $NetworkName ; and close the Vite process on port 5173"
    } else {
        Stop-Stack
    }
}

if ($failed) { exit 1 }
