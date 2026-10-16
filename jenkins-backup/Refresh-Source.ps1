param(
    [string]$RepositoryPath = (Join-Path $PSScriptRoot '..')
)
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath $RepositoryPath).Path
$bundleDir = Join-Path $PSScriptRoot 'source'
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null
git -C $repoPath fetch origin develop
if ($LASTEXITCODE -ne 0) { throw 'Could not fetch develop; snapshot was not refreshed' }
$pendingBundle = Join-Path $bundleDir 'develop.pending.bundle'
git -C $repoPath bundle create $pendingBundle origin/develop
if ($LASTEXITCODE -ne 0) { throw 'Could not create develop snapshot' }
git -C $repoPath bundle verify $pendingBundle
if ($LASTEXITCODE -ne 0) { throw 'Snapshot verification failed' }
Move-Item -LiteralPath $pendingBundle -Destination (Join-Path $bundleDir 'develop.bundle') -Force
git -C $repoPath rev-parse origin/develop
