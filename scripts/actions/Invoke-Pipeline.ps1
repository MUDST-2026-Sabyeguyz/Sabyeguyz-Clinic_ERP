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
try {
 $priorApiPort=$env:CLINIC_API_TEST_PORT
 $env:CLINIC_API_TEST_PORT='18083'
 Invoke-Checked npm.cmd @('ci')
 Invoke-Checked npm.cmd @('run','build')
 Invoke-Checked npm.cmd @('run','test:api')
 $count=0
 foreach($report in Get-ChildItem 'cypress/results/api-*.xml') {
  $xml=[xml](Get-Content $report.FullName -Raw)
  $count += @($xml.SelectNodes('//testcase[not(skipped)]')).Count
  if([int]$xml.DocumentElement.GetAttribute('failures') -ne 0 -or
     [int]$xml.DocumentElement.GetAttribute('errors') -ne 0 -or
     @($xml.SelectNodes('//failure | //error')).Count -ne 0) { throw 'API test report contains failures/errors.' }
 }
 if($count -lt 1) { throw 'API tests reported zero executed tests.' }
 New-Item -ItemType Directory -Force "$evidence/api-results" | Out-Null
 Copy-Item 'cypress/results/api-*.xml' "$evidence/api-results/"
} finally { $env:CLINIC_API_TEST_PORT=$priorApiPort; Pop-Location }
$images=@("clinic-actions-backend:$tag","clinic-actions-frontend:$tag")
Invoke-Checked docker @('build','--platform','linux/amd64','--label',"org.opencontainers.image.revision=$SourceSha",'-t',$images[0],'./backend')
Invoke-Checked docker @('build','--platform','linux/amd64','--label',"org.opencontainers.image.revision=$SourceSha",'-t',$images[1],'./frontend')
foreach($index in 0..1) {
 $name=@('backend','frontend')[$index]
 Invoke-Checked docker @('run','--rm','--mount','type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
 '--mount',"type=bind,source=$evidence,target=/reports",'--mount','type=volume,source=clinic-actions-trivy-cache,target=/root/.cache/',
 'aquasec/trivy:0.66.0','image','--exit-code','1','--severity','HIGH,CRITICAL','--format','json','--output',"/reports/trivy-$name.json",$images[$index])
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
