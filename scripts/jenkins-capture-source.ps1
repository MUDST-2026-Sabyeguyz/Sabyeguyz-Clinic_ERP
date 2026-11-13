param(
    [Parameter(Mandatory = $true)][string]$SourceRepo,
    [Parameter(Mandatory = $true)][string]$Branch,
    [Parameter(Mandatory = $true)][string]$LastAttemptedShaPath,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$sourceRevision = (& git -C $SourceRepo rev-parse --verify "refs/heads/$Branch^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or -not $sourceRevision) { throw "Cannot resolve local branch '$Branch' in '$SourceRepo'." }

$lastAttempted = ''
if (Test-Path -LiteralPath $LastAttemptedShaPath) {
    $lastAttempted = ([IO.File]::ReadAllText($LastAttemptedShaPath)).Trim()
}
$changed = $Force.IsPresent -or $sourceRevision -ne $lastAttempted
Write-Output "SOURCE_SHA=$sourceRevision"
Write-Output "SOURCE_CHANGED=$($changed.ToString().ToLowerInvariant())"
if (-not $changed) { return }

$workspace = (Get-Location).Path
if ((Get-ChildItem -LiteralPath $workspace -Force | Measure-Object).Count -gt 0) {
    throw "Jenkins source workspace is not empty: $workspace"
}
$ErrorActionPreference = 'Continue'
& git clone --quiet --no-hardlinks --no-checkout -- $SourceRepo .
$cloneExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($cloneExitCode -ne 0) { throw 'Source clone failed.' }
& git checkout --quiet --detach $sourceRevision
if ($LASTEXITCODE -ne 0) { throw 'Could not checkout the captured local branch revision.' }
