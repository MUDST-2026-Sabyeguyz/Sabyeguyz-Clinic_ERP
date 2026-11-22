param([switch]$SkipInstall, [ValidateSet('All', 'Browser')][string]$Mode = 'All')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/..").Path
. "$repo/deploy/k8s/Common.ps1"
Initialize-PrivateRuntime
$runtime = Join-Path $repo 'deploy/k8s/runtime/quality'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$dbName = 'clinic-submission-test-postgres'
$dbCreated = $false
$originalPath = $env:Path
$envNames = @('DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD', 'VITE_API_PROXY_TARGET', 'CYPRESS_API_URL', 'CYPRESS_BASE_URL', 'MAVEN_OPTS')
$previousEnvironment = @{}
foreach ($name in $envNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }
@{ result = 'RUNNING'; testedAt = (Get-Date).ToUniversalTime().ToString('o') } |
    ConvertTo-Json | Set-Content "$runtime/summary.json" -Encoding utf8
function Run([string]$Tool, [string[]]$ToolArgs) {
    & $Tool @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "$Tool $($ToolArgs -join ' ') failed ($LASTEXITCODE)." }
}
function Require-FreePort([int]$Port) {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw "Port $Port is busy; stop its owner or use a free test environment." } finally { $listener.Stop() }
}
try {
    foreach ($port in @(15439, 18080, 19001, 5173)) { Require-FreePort $port }
    Initialize-MavenTool
    $env:MAVEN_OPTS = ($env:MAVEN_OPTS + ' -Xmx512m').Trim()
    if ($Mode -eq 'All') {
        Push-Location "$repo/frontend"
        try {
            if (!$SkipInstall) { Run npm @('ci') }
            Run npm @('run', 'lint:check')
            Run npm @('run', 'test:coverage')
            Run npm @('run', 'build')
        } finally { Pop-Location }
    }
    Push-Location "$repo/backend"
    try {
        if (!$SkipInstall) { Run npm @('ci') }
        if ($Mode -eq 'All') {
            Run npm @('run', 'test:coverage')
        }
        Run docker @('run', '-d', '--name', $dbName, '-p', '127.0.0.1:15439:5432', '-e', 'POSTGRES_USER=clinic', '-e', 'POSTGRES_PASSWORD=isolated_test_only', '-e', 'POSTGRES_DB=clinic_submission_test', 'postgres:16-alpine')
        $dbCreated = $true
        $dbReady = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            & docker exec $dbName pg_isready -U clinic -d clinic_submission_test *> $null
            if ($LASTEXITCODE -eq 0) { $dbReady = $true; break }
            Start-Sleep -Seconds 2
        }
        if (!$dbReady) { throw 'Isolated test PostgreSQL did not become ready.' }
        $env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15439/clinic_submission_test'
        $env:DATABASE_USERNAME = 'clinic'
        $env:DATABASE_PASSWORD = 'isolated_test_only'
        if ($Mode -eq 'All') { Run npm @('run', 'test:v2:coverage') }
    } finally { Pop-Location }
    if ($Mode -eq 'Browser' -and !$SkipInstall) {
        Push-Location "$repo/frontend"
        try { Run npm @('ci') } finally { Pop-Location }
    }
    Push-Location "$repo/backend"
    try { Run npm @('run', 'test:browser') } finally { Pop-Location }
    $checked = if ($Mode -eq 'All') { @('ESLint', 'Vue TypeScript and production build', 'Vitest with V8 coverage', 'Backend Cypress regression with JaCoCo', 'V2 PostgreSQL API tests with JaCoCo', 'V2 browser E2E on real isolated API') } else { @('V2 browser E2E on real isolated API') }
    @{ result = 'PASS'; mode = $Mode; testedAt = (Get-Date).ToUniversalTime().ToString('o'); sourceRevision = (& git -C $repo rev-parse HEAD).Trim(); checks = $checked } |
        ConvertTo-Json | Set-Content "$runtime/summary.json" -Encoding utf8
} catch {
    @{ result = 'FAIL'; testedAt = (Get-Date).ToUniversalTime().ToString('o') } |
        ConvertTo-Json | Set-Content "$runtime/summary.json" -Encoding utf8
    throw
} finally {
    if ($dbCreated) { & docker rm -f -v $dbName | Out-Host }
    $env:Path = $originalPath
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name]) }
}
