param([string]$ProjectName)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoPath
if (-not (Test-Path -LiteralPath '.env')) {
    throw 'Create .env from .env.example and configure the database and JWT secrets first.'
}

if (-not $ProjectName) {
    # Reuse the running application's project so its database volume stays attached.
    $containerIds = @(docker ps --filter 'label=com.docker.compose.service=frontend' --format '{{.ID}}')
    if ($LASTEXITCODE -ne 0) { throw 'Docker is not available.' }
    $projects = @()
    foreach ($containerId in $containerIds) {
        $containerInfo = docker inspect $containerId | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the running application.' }
        $labels = $containerInfo[0].Config.Labels
        $workingDir = $labels.'com.docker.compose.project.working_dir'
        if ($workingDir -and [IO.Path]::GetFullPath($workingDir).TrimEnd('\', '/') -eq $repoPath.TrimEnd('\', '/')) {
            $projects += $labels.'com.docker.compose.project'
        }
    }
    $projects = @($projects | Select-Object -Unique)
    if ($projects.Count -gt 1) { throw 'Multiple applications use this folder. Run again with -ProjectName <existing-project>.' }
    if ($projects.Count -eq 1) { $ProjectName = $projects[0] }
}

$composeArgs = @('compose', '-f', 'compose.yaml')
if ($ProjectName) { $composeArgs += @('-p', $ProjectName) }
& docker @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid.' }
& docker @composeArgs up --build --force-recreate -d --wait backend frontend
if ($LASTEXITCODE -ne 0) { throw 'Application update failed. Check docker compose logs.' }
& docker @composeArgs ps
Write-Host ('Updated source: ' + (git rev-parse --short HEAD))
