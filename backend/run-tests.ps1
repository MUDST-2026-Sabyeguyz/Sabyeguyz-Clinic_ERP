param(
    [ValidateSet('all', 'api')]
    [string]$Suite = 'all',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = 'Stop'

# Respect JAVA_HOME, or use the JDK already on PATH (including CI's JDK 21).
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    throw 'Install JDK 17 or newer and set JAVA_HOME.'
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw 'Maven was not found in PATH.'
}
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw 'Node.js/npm is required for Cypress. Install Node.js 24 and run npm ci in backend.'
}
if ($Suite -eq 'api' -and $MavenArgs.Count -gt 0) {
    throw 'Maven arguments apply only to the build. Use npm run test:api -- --spec <path> to select a Cypress spec.'
}

Push-Location $PSScriptRoot
try {
    if ($Suite -ne 'api') {
        & mvn --batch-mode --no-transfer-progress clean package @MavenArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & npm run test:api
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
