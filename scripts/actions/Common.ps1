$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$script:Namespace = 'clinic-actions-demo'
$script:Runtime = Join-Path $env:LOCALAPPDATA 'ClinicActionsDeploy'
function Invoke-Checked([string]$Tool, [string[]]$ToolArgs) {
    & $Tool @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}
function Invoke-DeployKube {
    if (-not $env:KUBECONFIG -or $env:KUBECONFIG -ne (Join-Path $script:Runtime 'runner.kubeconfig')) {
        throw 'Use only the separately provisioned runner kubeconfig.'
    }
    Invoke-Checked kubectl (@('--namespace', $script:Namespace, '--request-timeout=30s') + $args)
}
function Protect-Directory([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    Invoke-Checked icacls.exe @($Path, '/inheritance:r', '/grant:r', "${identity}:(OI)(CI)F") | Out-Null
}
