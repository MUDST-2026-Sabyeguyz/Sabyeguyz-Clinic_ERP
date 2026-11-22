$ErrorActionPreference = 'Stop'
$KubeRoot = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $KubeRoot '../..')).Path
$RuntimeRoot = Join-Path $KubeRoot 'runtime'
function Assert-LocalContext([string]$Context) {
    if ($Context -notmatch '^(docker-desktop|minikube|kind-clinic-ci)$') {
        throw "Only docker-desktop, minikube, or kind-clinic-ci are allowed; received '$Context'."
    }
    & kubectl --context $Context --request-timeout=15s cluster-info | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Local Kubernetes is unavailable. Enable Kubernetes in Docker Desktop or start Minikube.' }
}
function Invoke-Kube {
    & kubectl --context $Context @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($args -join ' ')" }
}
function Invoke-Tool([string]$Tool, [string[]]$ToolArgs) {
    & $Tool @ToolArgs | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed ($LASTEXITCODE)." }
}
function Initialize-PrivateRuntime {
    New-Item -ItemType Directory -Force -Path $RuntimeRoot | Out-Null
    if ($env:OS -eq 'Windows_NT') {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe $RuntimeRoot /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not protect local runtime files.' }
    } else { & chmod 700 $RuntimeRoot }
}
function New-RandomSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function Initialize-MavenTool {
    if (!(Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install JDK 17+ and set JAVA_HOME first.' }
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return }
    $mavenRoot = Join-Path $RuntimeRoot 'maven'
    if (!(Test-Path "$mavenRoot/bin/mvn.cmd") -and !(Test-Path "$mavenRoot/bin/mvn")) {
        $helper = 'clinic-maven-tools-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        Invoke-Tool docker @('create', '--name', $helper, 'maven:3.9-eclipse-temurin-21')
        try { Invoke-Tool docker @('cp', "${helper}:/usr/share/maven", $mavenRoot) } finally { Invoke-Tool docker @('rm', $helper) }
    }
    $env:Path = (Join-Path $mavenRoot 'bin') + [IO.Path]::PathSeparator + $env:Path
}
