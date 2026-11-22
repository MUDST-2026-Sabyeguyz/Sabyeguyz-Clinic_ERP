[CmdletBinding(SupportsShouldProcess)]
param([Parameter(Mandatory)][string]$PreviousReleaseFile)
. "$PSScriptRoot/Common.ps1"
$env:KUBECONFIG=Join-Path $script:Runtime 'runner.kubeconfig'
$previous=Get-Content -LiteralPath $PreviousReleaseFile -Raw | ConvertFrom-Json
foreach($deployment in $previous.deployments) {
 if($deployment.name -notin @('backend','frontend')) { continue }
 $image=@($deployment.images)
 if($image.Count -ne 1 -or $image[0] -notmatch '^clinic-actions-(backend|frontend):[a-f0-9]{12}-[0-9]+-[0-9]+$') { throw 'Unrecognized previous application image.' }
 if($PSCmdlet.ShouldProcess("$script:Namespace/$($deployment.name)",'Restore previous application image only')) {
  Invoke-DeployKube set image "deployment/$($deployment.name)" "$($deployment.name)=$($image[0])"
  Invoke-DeployKube rollout status "deployment/$($deployment.name)" --timeout=360s
 }
}
Write-Warning 'This restores application images only. Database migrations are NOT reverted; assess schema compatibility before rollback.'
