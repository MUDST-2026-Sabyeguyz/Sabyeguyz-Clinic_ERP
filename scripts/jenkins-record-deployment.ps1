param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{40}$')]
    [string]$SourceSha,
    [string]$StatePath = (Join-Path $env:LOCALAPPDATA 'ClinicJenkinsAgent\last-deployed-sha.txt')
)

$ErrorActionPreference = 'Stop'
$parent = Split-Path -Parent $StatePath
[IO.Directory]::CreateDirectory($parent) | Out-Null
[IO.File]::WriteAllText($StatePath, "$SourceSha`n", (New-Object Text.UTF8Encoding($false)))
Write-Host "Recorded revision $SourceSha at $StatePath"
