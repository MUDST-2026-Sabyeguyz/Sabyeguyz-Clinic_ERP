[CmdletBinding(SupportsShouldProcess)]
param()
. "$PSScriptRoot/Common.ps1"
$pidFile=Join-Path $script:Runtime 'runner.pid'
if(-not (Test-Path $pidFile)) { return }
$runnerPid=[int](Get-Content $pidFile)
$process=Get-Process -Id $runnerPid -ErrorAction SilentlyContinue
$expected=Join-Path $env:LOCALAPPDATA 'ClinicActionsRunner/bin/Runner.Listener.exe'
if($process -and $process.Path -ne $expected) { throw 'PID was reused by another process; refusing to stop it.' }
if($process -and $PSCmdlet.ShouldProcess('Dedicated Clinic Actions runner','Stop listener after confirming no deployment is active')) {
 Stop-Process -Id $runnerPid
}
