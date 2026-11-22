[CmdletBinding()]
param()
. "$PSScriptRoot/Common.ps1"
$runnerRoot=Join-Path $env:LOCALAPPDATA 'ClinicActionsRunner'
if(-not (Test-Path (Join-Path $runnerRoot '.runner'))) { throw 'Install the dedicated runner first.' }
$toolchain=Get-Content (Join-Path $script:Runtime 'toolchain.json') -Raw | ConvertFrom-Json
if ($toolchain.node24Directory) { $env:PATH=$toolchain.node24Directory+';'+$env:PATH }
$env:KUBECONFIG=Join-Path $script:Runtime 'runner.kubeconfig'
$pidFile=Join-Path $script:Runtime 'runner.pid'
if(Test-Path $pidFile) {
 $prior=Get-Process -Id ([int](Get-Content $pidFile)) -ErrorAction SilentlyContinue
 if($prior -and $prior.ProcessName -eq 'Runner.Listener') { throw 'This runner is already running.' }
}
$process=Start-Process (Join-Path $runnerRoot 'bin/Runner.Listener.exe') -ArgumentList @('run','--startuptype','manual') -WorkingDirectory $runnerRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $script:Runtime 'runner-output.log') -RedirectStandardError (Join-Path $script:Runtime 'runner-error.log')
$process.Id | Set-Content $pidFile
Write-Host 'Dedicated runner launched in background. Its diagnostics remain outside Git.'
