[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$SourceSha)
. "$PSScriptRoot/Common.ps1"
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
if ((Invoke-Checked git @('rev-parse','HEAD')).Trim() -ne $SourceSha) { throw 'Source revision mismatch.' }
if ($env:GITHUB_RUN_ID -notmatch '^\d+$' -or $env:GITHUB_RUN_ATTEMPT -notmatch '^\d+$') { throw 'Run this entry point from the reviewed GitHub workflow.' }
$tag="$($SourceSha.Substring(0,12))-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
$evidence=Join-Path $root 'actions-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
if (@(Get-ChildItem $evidence -Force).Count -ne 0) { throw 'Evidence directory must be fresh; refusing to mix releases.' }
$env:KUBECONFIG=Join-Path $script:Runtime 'runner.kubeconfig'
if (-not (Test-Path $env:KUBECONFIG)) { throw 'Run separate admin provisioning first.' }
$expires=[DateTimeOffset]::Parse((Get-Content (Join-Path $script:Runtime 'token-expires.txt') -Raw).Trim())
if ($expires -lt [DateTimeOffset]::UtcNow.AddMinutes(65)) { throw 'Renew the runner Kubernetes token before this run.' }
$major=(Invoke-Checked node @('-p','process.versions.node.split(".")[0]')).Trim()
if ($major -ne '24') { throw 'Node 24 is required.' }
Push-Location frontend
try { Invoke-Checked npm.cmd @('ci'); Invoke-Checked npm.cmd @('run','test:unit','--','--run'); Invoke-Checked npm.cmd @('run','build') } finally { Pop-Location }
Push-Location backend
$testContainer = "clinic-api-test-$env:GITHUB_RUN_ID-$env:GITHUB_RUN_ATTEMPT"
$testPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$testVariables = @('CLINIC_V2_TEST_PORT', 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD')
$previousTestEnvironment = @{}
foreach ($name in $testVariables) { $previousTestEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }
$testContainerId = $null
try {
 $testContainerId = Invoke-Checked docker @('run','-d','--name',$testContainer,
     '-p','127.0.0.1::5432','-e','POSTGRES_DB=clinic_v2_test','-e','POSTGRES_USER=clinic',
     '-e',"POSTGRES_PASSWORD=$testPassword",'postgres:16-alpine')
 $testMapping = (Invoke-Checked docker @('port',$testContainerId,'5432/tcp')).Trim()
 if ($testMapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Expected a loopback-only PostgreSQL test port.' }
 $env:DATABASE_URL = "jdbc:postgresql://$testMapping/clinic_v2_test"
 $env:DATABASE_USERNAME = 'clinic'
 $env:DATABASE_PASSWORD = $testPassword
 $env:CLINIC_V2_TEST_PORT = '18082'
 $testDbReady = $false
 for ($attempt = 0; $attempt -lt 30; $attempt++) {
  & docker exec $testContainerId pg_isready -h 127.0.0.1 -U clinic -d clinic_v2_test *> $null
  if ($LASTEXITCODE -eq 0) { $testDbReady = $true; break }
  Start-Sleep -Seconds 2
 }
 if (-not $testDbReady) { throw 'Disposable PostgreSQL did not become ready.' }
 Invoke-Checked npm.cmd @('ci')
 Invoke-Checked npm.cmd @('run','build')
 Invoke-Checked npm.cmd @('run','test:api')
 $count=0
 foreach($report in Get-ChildItem 'cypress/results/v2-*.xml') {
  $xml=[xml](Get-Content $report.FullName -Raw)
  $count += @($xml.SelectNodes('//testcase[not(skipped)]')).Count
  if([int]$xml.DocumentElement.GetAttribute('failures') -ne 0 -or
     [int]$xml.DocumentElement.GetAttribute('errors') -ne 0 -or
     @($xml.SelectNodes('//failure | //error')).Count -ne 0) { throw 'API test report contains failures/errors.' }
 }
 if($count -lt 1) { throw 'API tests reported zero executed tests.' }
 New-Item -ItemType Directory -Force "$evidence/api-results" | Out-Null
 Copy-Item 'cypress/results/v2-*.xml' "$evidence/api-results/"
 Invoke-Checked npm.cmd @('run','test:browser')
} finally {
 foreach ($name in $testVariables) { [Environment]::SetEnvironmentVariable($name, $previousTestEnvironment[$name]) }
 Pop-Location
 if ($testContainerId) { Invoke-Checked docker @('rm','-f','-v',$testContainerId) | Out-Null }
}
$images=@("clinic-actions-backend:$tag","clinic-actions-frontend:$tag")
Invoke-Checked docker @('build','--platform','linux/amd64','--label',"org.opencontainers.image.revision=$SourceSha",'-t',$images[0],'./backend')
Invoke-Checked docker @('build','--platform','linux/amd64','--label',"org.opencontainers.image.revision=$SourceSha",'-t',$images[1],'./frontend')
$scanner='aquasec/trivy:0.66.0@sha256:086971aaf400beebd94e8300fd8ea623774419597169156cec56eec5b00dfb1e'
Invoke-Checked docker @('pull',$scanner)
foreach($index in 0..1) {
 $name=@('backend','frontend')[$index]
 Invoke-Checked docker @('run','--rm','--mount','type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
 '--mount','type=volume,source=clinic-actions-trivy-cache,target=/root/.cache/',
 $scanner,'image','--timeout','10m','--exit-code','1','--severity','HIGH,CRITICAL','--format','json',$images[$index]) |
 Set-Content "$evidence/trivy-$name.json" -Encoding utf8
}
Invoke-Checked docker @('pull','--platform','linux/amd64','postgres:16-alpine')
Invoke-Checked python @('./scripts/actions/import-images.py','--nodes',(Join-Path $script:Runtime 'nodes.json'),$images[0],$images[1],'postgres:16-alpine')
& "$PSScriptRoot/Render-Manifests.ps1" -SourceSha $SourceSha -ImageTag $tag -OutputDirectory "$evidence/manifests"
$previous=Invoke-DeployKube get deployments -o json | ConvertFrom-Json
@{capturedAt=[DateTimeOffset]::UtcNow.ToString('o');deployments=@($previous.items | ForEach-Object { @{name=$_.metadata.name;images=@($_.spec.template.spec.containers.image)} })} |
 ConvertTo-Json -Depth 10 | Set-Content "$evidence/previous-release.json" -Encoding utf8
try {
 Invoke-DeployKube apply -f "$evidence/manifests/database.json"
 Invoke-DeployKube rollout status deployment/postgres --timeout=300s
 Invoke-DeployKube apply -f "$evidence/manifests/migration.json"
 Invoke-DeployKube wait --for=condition=complete "job/migrate-$tag" --timeout=310s
 Invoke-DeployKube apply -f "$evidence/manifests/application.json"
 foreach($deployment in @('backend','frontend')) { Invoke-DeployKube rollout status "deployment/$deployment" --timeout=360s }
 & "$PSScriptRoot/Verify-Release.ps1" -SourceSha $SourceSha -ImageTag $tag -EvidenceDirectory $evidence
} catch {
 Invoke-DeployKube get events --sort-by=.lastTimestamp | Set-Content "$evidence/events.txt"
 throw
}
@{sourceSha=$SourceSha;imageTag=$tag;namespace=$script:Namespace;runId=$env:GITHUB_RUN_ID;runAttempt=$env:GITHUB_RUN_ATTEMPT;verified=$true} |
 ConvertTo-Json | Set-Content "$evidence/release.json" -Encoding utf8
if ($env:GITHUB_STEP_SUMMARY) {
 "## Verified isolated Kubernetes release`nSource: $SourceSha`nNamespace: $script:Namespace`nImages: $($images -join ', ')`nRollout, health and authenticated read smoke passed." | Add-Content $env:GITHUB_STEP_SUMMARY
}
