[CmdletBinding()]
param([switch]$DemoSeed)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
$project=if($DemoSeed){'clinic-phase1-review-demo'}else{'clinic-phase1-review'}
$envFile=Join-Path $root $(if($DemoSeed){'.env.phase1-demo'}else{'.env.phase1'})
function New-LocalSecret {
    $bytes=New-Object byte[] 32
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
    ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
}
& docker info --format '{{.OSType}}'
if($LASTEXITCODE -ne 0){throw 'Start Docker Desktop using Linux containers first'}
if(-not(Test-Path -LiteralPath $envFile)){
    $existing=@(& docker volume ls --filter "name=^${project}_postgres-v2-data$" --format '{{.Name}}')
    if($LASTEXITCODE -ne 0){throw 'Volume inventory failed'}
    if($existing.Count){throw 'Existing data found without this environment file. Recover its credentials instead of generating new ones.'}
    $lines=@('POSTGRES_DB=clinic_erp_v2','POSTGRES_USER=clinic',('POSTGRES_PASSWORD='+(New-LocalSecret)),('JWT_SECRET='+(New-LocalSecret)),'POSTGRES_PORT=15432','BACKEND_PORT=127.0.0.1:18081','FRONTEND_PORT=127.0.0.1:18082')
    if(-not $DemoSeed){$lines+=@('CLINIC_BOOTSTRAP_MANAGER_USERNAME=phase1.manager','CLINIC_BOOTSTRAP_MANAGER_EMAIL=phase1.manager@example.invalid',('CLINIC_BOOTSTRAP_MANAGER_PASSWORD='+(New-LocalSecret)))}
    [IO.File]::WriteAllLines($envFile,$lines,(New-Object Text.UTF8Encoding($false)))
    $lines=$null
}
$compose=@('compose','-p',$project,'--env-file',$envFile,'-f',(Join-Path $root 'compose.yaml'))
if($DemoSeed){$compose+=@('-f',(Join-Path $root 'compose.phase1-demo.yaml'))}
& docker @compose config --quiet
if($LASTEXITCODE -ne 0){throw 'Compose configuration is invalid'}
& docker @compose up --build -d --wait --wait-timeout 300
if($LASTEXITCODE -ne 0){throw "Startup failed. Inspect: docker compose -p $project --env-file $envFile logs --tail 100 backend"}
& docker @compose ps
Write-Output 'Open http://localhost:18082/ ; health http://localhost:18081/actuator/health'
if($DemoSeed){Write-Output 'Synthetic demo login only: manager / manager123. This database is separate from real/legacy volumes.'}
else{Write-Output 'Initial Manager: phase1.manager ; read the bootstrap password locally in .env.phase1 and retain it securely.'}
Write-Output "Project: $project ; environment file: $envFile ; keep this file to reopen the same data."
