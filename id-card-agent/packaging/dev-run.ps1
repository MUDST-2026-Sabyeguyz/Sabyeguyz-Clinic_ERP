# Runs the agent from the built jar for testing, without installing anything.
#
#   .\dev-run.ps1 -Origins "http://localhost"            # the Docker Compose frontend (nginx on port 80)
#   .\dev-run.ps1 -Origins "http://localhost:5173"       # the Vite dev server
#   .\dev-run.ps1 -Origins "http://localhost" -Fresh     # start with no pairing at all (separate temp home, wiped)
#   .\dev-run.ps1 -Origins "http://localhost" -KeepTestHome  # same temp home, keeping its pairing (to test restart/resume)
#   .\dev-run.ps1 -Command read-once                      # try the reader and card from the command line
#
# Without -Fresh the agent uses your real profile folder (~/.clinic-id-card-agent), like an installed one would.
param(
    [string]$Origins = 'http://localhost',
    [switch]$Fresh,
    [switch]$KeepTestHome,
    [switch]$Rebuild,
    [ValidateSet('run', 'read-once')][string]$Command = 'run'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Find-Java17 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $candidates += (Get-ChildItem 'C:\Program Files\Microsoft\jdk-17*\bin\java.exe', 'C:\Program Files\Eclipse Adoptium\jdk-17*\bin\java.exe',
        'C:\Program Files\Java\jdk-17*\bin\java.exe' -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($java in $candidates) {
        if (-not (Test-Path $java)) { continue }
        # java prints its version on stderr, which 'Stop' would turn into an error
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $version = (& $java -version 2>&1 | Select-Object -First 1).ToString()
        $ErrorActionPreference = $previous
        if ($version -match '"(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17) { return $java }
        }
    }
    throw 'JDK 17+ not found. Install one or set JAVA_HOME.'
}

$java = Find-Java17
$jar = Join-Path $root 'target\id-card-agent.jar'
if ($Rebuild -or -not (Test-Path $jar)) {
    Write-Host 'Building the agent...'
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
    Push-Location $root
    try { mvn -q package -DskipTests; if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' } } finally { Pop-Location }
}

$javaArgs = @("-Dclinic.allowedOrigins=$Origins")
if ($Fresh -or $KeepTestHome) {
    $testHome = Join-Path ([IO.Path]::GetTempPath()) 'clinic-id-card-agent-test-home'
    if ($Fresh -and (Test-Path $testHome)) { Remove-Item -Recurse -Force $testHome }
    New-Item -ItemType Directory -Path $testHome -Force | Out-Null
    $javaArgs += "-Duser.home=$testHome"
    Write-Host ($(if ($Fresh) { 'Fresh home (no pairing): ' } else { 'Test home (keeps its pairing): ' }) + $testHome)
}
Write-Host "Java:    $java"
Write-Host "Allowed: $Origins"
Write-Host 'Stop with Ctrl+C. Status check: curl -H "Origin: <allowed origin>" http://127.0.0.1:47631/status'
& $java @javaArgs -jar $jar $Command
