[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$SourceSha,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{12}-[0-9]+-[0-9]+$')][string]$ImageTag,
 [Parameter(Mandatory)][string]$EvidenceDirectory)
. "$PSScriptRoot/Common.ps1"
$deployments=Invoke-DeployKube get deployments -o json | ConvertFrom-Json
foreach($name in @('backend','frontend')) {
 $deployment=@($deployments.items | Where-Object {$_.metadata.name -eq $name})
 if($deployment.Count -ne 1 -or $deployment[0].metadata.annotations.'clinic.source-sha' -ne $SourceSha -or
    $deployment[0].spec.template.spec.containers[0].image -ne "clinic-actions-${name}:$ImageTag") { throw "Deployment revision mismatch: $name" }
}
$pods=Invoke-DeployKube get pods -l 'app in (backend,frontend)' -o json | ConvertFrom-Json
$verifiedPods=@()
foreach($pod in $pods.items) {
 if($pod.metadata.labels.app -notin @('backend','frontend')) { continue }
 foreach($container in $pod.status.containerStatuses) {
  if(-not $container.ready -or -not $container.imageID) { throw 'Application pod is not ready with a resolved image.' }
  $verifiedPods+=@{pod=$pod.metadata.name;image=$container.image;imageID=$container.imageID}
 }
}
if($verifiedPods.Count -lt 2) { throw 'Missing verified application pods.' }
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,18190)
try { $listener.Start() } finally { $listener.Stop() }
$forward=Start-Process kubectl -ArgumentList @('--kubeconfig',$env:KUBECONFIG,'-n',$script:Namespace,'port-forward','--address=127.0.0.1','service/frontend','18190:80') -PassThru -WindowStyle Hidden -RedirectStandardOutput "$EvidenceDirectory/port-forward.txt" -RedirectStandardError "$EvidenceDirectory/port-forward-error.txt"
try {
 $ready=$false
 for($i=0;$i -lt 30;$i++) {
  if($forward.HasExited) { throw 'Port-forward process exited.' }
  try { $response=Invoke-WebRequest 'http://127.0.0.1:18190/healthz' -TimeoutSec 3; $ready=$response.StatusCode -eq 200 } catch {}
  if($ready) { break }; Start-Sleep -Seconds 1
 }
 if(-not $ready) { throw 'Frontend health timeout.' }
 $credentials=Get-Content (Join-Path $script:Runtime 'credentials.json') -Raw | ConvertFrom-Json
 $login=Invoke-RestMethod 'http://127.0.0.1:18190/api/auth/login' -Method Post -ContentType 'application/json' -Body (@{username=$credentials.MANAGER_USERNAME;password=$credentials.MANAGER_PASSWORD}|ConvertTo-Json)
 if(-not $login.token -or $login.role -ne 'MANAGER') { throw 'Manager authentication smoke failed.' }
 $branches=Invoke-WebRequest 'http://127.0.0.1:18190/api/branches' -Headers @{Authorization="Bearer $($login.token)"}
 if($branches.StatusCode -ne 200) { throw 'Authenticated read smoke failed.' }
 $anonymous=Invoke-WebRequest 'http://127.0.0.1:18190/api/branches' -SkipHttpErrorCheck
 if($anonymous.StatusCode -ne 401) { throw 'Anonymous protected API did not return 401.' }
 @{frontendStatus=200;authenticatedReadStatus=200;anonymousApiStatus=401;sourceSha=$SourceSha;pods=$verifiedPods} |
   ConvertTo-Json -Depth 10 | Set-Content "$EvidenceDirectory/smoke.json" -Encoding utf8
} finally { if(-not $forward.HasExited) { Stop-Process -Id $forward.Id } }
