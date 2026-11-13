[CmdletBinding()]
param([string]$Node24Directory = '')
. "$PSScriptRoot/Common.ps1"
$repo='MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP'
$runnerRoot=Join-Path $env:LOCALAPPDATA 'ClinicActionsRunner'
if (Test-Path (Join-Path $runnerRoot '.runner')) { throw 'Runner already configured. Use Start-Runner.ps1; do not replace an existing registration.' }
if ($Node24Directory) { $env:PATH=$Node24Directory+';'+$env:PATH }
if ((Invoke-Checked node @('-p','process.versions.node.split(".")[0]')).Trim() -ne '24') { throw 'Provide an existing Node24Directory.' }
foreach($tool in @('gh','docker','kubectl','java','mvn.cmd','python','pwsh')) { if(-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing runner prerequisite: $tool" } }
Protect-Directory $runnerRoot
Protect-Directory $script:Runtime
$packages=Invoke-Checked gh @('api',"repos/$repo/actions/runners/downloads") | ConvertFrom-Json
$package=@($packages | Where-Object {$_.os -eq 'win' -and $_.architecture -eq 'x64'})[0]
if ($package.download_url -notmatch '^https://github.com/actions/runner/releases/download/' -or $package.sha256_checksum -notmatch '^[a-f0-9]{64}$') { throw 'Unrecognized official runner package metadata.' }
$archive=Join-Path $script:Runtime 'runner.zip'
Invoke-WebRequest $package.download_url -OutFile $archive
if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.sha256_checksum) { throw 'Runner download checksum mismatch.' }
Expand-Archive -LiteralPath $archive -DestinationPath $runnerRoot -Force
@{node24Directory=$Node24Directory} | ConvertTo-Json | Set-Content (Join-Path $script:Runtime 'toolchain.json') -Encoding utf8
$registration=Invoke-Checked gh @('api','--method','POST',"repos/$repo/actions/runners/registration-token") | ConvertFrom-Json
Push-Location $runnerRoot
try {
 & ./config.cmd --url "https://github.com/$repo" --token $registration.token --name clinic-actions-demo-windows --labels clinic-k8s-deploy --unattended --work _work
 if ($LASTEXITCODE -ne 0) { throw 'Runner registration failed.' }
} finally { $registration=$null; Pop-Location }
Write-Host 'Repository runner registered. Actions remains unchanged. Start explicitly with Start-Runner.ps1.'
