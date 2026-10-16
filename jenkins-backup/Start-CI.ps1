param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtime = Join-Path $PSScriptRoot 'runtime'
$source = Join-Path $PSScriptRoot 'source'
New-Item -ItemType Directory -Force -Path $runtime,$source | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls.exe $runtime /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
$passwordFile = Join-Path $runtime 'admin-password'
if (!(Test-Path -LiteralPath $passwordFile)) {
    $bytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes))
}
$token = gh auth token
if ($LASTEXITCODE -ne 0) { throw 'Sign into GitHub CLI before starting CI' }
[IO.File]::WriteAllText((Join-Path $runtime 'github-token'), $token.Trim())
Remove-Variable token
git -C $repo fetch origin --prune
if ($LASTEXITCODE -ne 0) { throw 'Could not fetch source' }
git -C $repo bundle create (Join-Path $source 'all.bundle') --all
if ($LASTEXITCODE -ne 0) { throw 'Could not create source bundle' }
$compose = Join-Path $PSScriptRoot 'compose.ci.yaml'
if (!$SkipBuild) {
    docker compose -f $compose build jenkins
    if ($LASTEXITCODE -ne 0) { throw 'Jenkins image build failed' }
}
docker compose -f $compose up -d
if ($LASTEXITCODE -ne 0) { throw 'Could not start Jenkins' }
docker compose -f $compose exec -T -u root jenkins chown 1000:1000 /var/jenkins_home/workspace /var/jenkins_home/ci-worker-home /var/jenkins_home/clinic-source.git
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    docker compose -f $compose exec -T docker docker info *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 2
}
if ($LASTEXITCODE -ne 0) { throw 'Isolated Docker daemon did not start' }
docker build -f (Join-Path $PSScriptRoot 'Dockerfile.worker') -t clinic-ci-worker:local $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'CI worker image build failed' }
& $env:ComSpec /d /c 'docker image save clinic-ci-worker:local | docker exec -i clinic-jenkins-ci-docker-1 docker image load'
if ($LASTEXITCODE -ne 0) { throw 'Could not load the CI worker image' }
docker compose -f $compose exec -T jenkins bash -c 'if [ -f /var/jenkins_home/clinic-source.git/HEAD ]; then git --git-dir=/var/jenkins_home/clinic-source.git fetch /opt/clinic/ci/source/all.bundle "+refs/*:refs/*"; else git clone --mirror /opt/clinic/ci/source/all.bundle /var/jenkins_home/clinic-source.git; fi'
if ($LASTEXITCODE -ne 0) { throw 'Could not initialize CI source' }
node (Join-Path $PSScriptRoot 'control.cjs') reload
if ($LASTEXITCODE -ne 0) { throw 'Could not refresh Jenkins jobs' }
Write-Host 'Jenkins: http://localhost:8086/ (user: clinic-admin)'
Write-Host 'Read runtime/admin-password locally to sign in. Do not share this file.'
Write-Host 'Run node jenkins-backup/control.cjs watch once to initialize branch tracking.'
