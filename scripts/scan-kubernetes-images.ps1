param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$')]
    [string]$ImageTag,
    [string]$TrivyImage = 'aquasec/trivy:0.76.1',
    [string]$ReportDirectory
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoPath
$reportsPath = if ($ReportDirectory) { [IO.Path]::GetFullPath($ReportDirectory) } else { Join-Path $repoPath 'reports' }
[IO.Directory]::CreateDirectory($reportsPath) | Out-Null

foreach ($imageName in @('clinic-backend', 'clinic-frontend')) {
    $image = "docker.io/library/${imageName}:$ImageTag"
    $archive = Join-Path ([IO.Path]::GetTempPath()) ("clinic-scan-$imageName-" + [guid]::NewGuid().ToString('N') + '.tar')
    $report = "trivy-$imageName.json"
    try {
        & docker image inspect $image *> $null
        if ($LASTEXITCODE -ne 0) { throw "Image is missing before scan: $image" }
        & docker image save --output $archive $image
        if ($LASTEXITCODE -ne 0) { throw "Could not export image for scanning: $image" }

        $archiveMount = "type=bind,source=$archive,target=/tmp/image.tar,readonly"
        $reportsMount = "type=bind,source=$reportsPath,target=/reports"
        & docker run --rm --mount $archiveMount --mount $reportsMount --mount 'type=volume,source=clinic-trivy-cache,target=/root/.cache/trivy' $TrivyImage image --input /tmp/image.tar --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --format json --output "/reports/$report"
        if ($LASTEXITCODE -ne 0) { throw "Trivy found HIGH/CRITICAL vulnerabilities or failed scanning $image. See reports/$report." }
        Write-Host "TRIVY_PASS=$image"
    } finally {
        if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive }
    }
}
