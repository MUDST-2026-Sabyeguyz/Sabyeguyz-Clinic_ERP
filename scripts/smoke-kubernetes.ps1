param(
    [string]$KubeContext = 'docker-desktop',
    [string]$Namespace = 'clinic-present',
    [switch]$CheckDemoLogin
)
$ErrorActionPreference = 'Stop'
$outLog = Join-Path ([IO.Path]::GetTempPath()) ('clinic-forward-' + [guid]::NewGuid().ToString('N') + '.out')
$errLog = $outLog + '.err'
$forward = $null
try {
    $portReservation = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $portReservation.Start()
    $port = $portReservation.LocalEndpoint.Port
    $portReservation.Stop()
    $arguments = @('--context',$KubeContext,'-n',$Namespace,'port-forward','service/frontend',"${port}:80",'--address','127.0.0.1')
    $arguments = @($arguments | ForEach-Object { '"' + $_.Replace('"','\"') + '"' })
    $kubectlCommand = Get-Command kubectl -All | Where-Object { $_.Source -notmatch '(?i)\\chocolatey\\bin\\kubectl\.exe$' } | Select-Object -First 1
    if (-not $kubectlCommand) { $kubectlCommand = Get-Command kubectl -ErrorAction Stop }
    $forward = Start-Process -FilePath $kubectlCommand.Source -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    $deadline = (Get-Date).AddSeconds(45)
    $baseUrl = "http://127.0.0.1:$port"
    $page = $null
    do {
        if ($forward.HasExited) { throw ('Port-forward failed: ' + [IO.File]::ReadAllText($errLog)) }
        try {
            $page = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/staff/settings" -TimeoutSec 2
            if ($page.StatusCode -eq 200) { break }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    if (-not $page -or $page.StatusCode -ne 200) { throw 'Port-forward did not become ready.' }
    if ($page.StatusCode -ne 200 -or [string]$page.Content -notmatch '(?i)html|115\s+99') { throw 'Frontend page did not respond successfully.' }
    $protectedStatus = 0
    try { $protectedStatus = [int](Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/patients?size=1" -TimeoutSec 20).StatusCode }
    catch { if ($_.Exception.Response) { $protectedStatus = [int]$_.Exception.Response.StatusCode } else { throw } }
    if ($protectedStatus -ne 401) { throw "Expected protected backend API to return 401 through nginx; received $protectedStatus." }
    $loginPassed = $null
    $patients = $null
    if ($CheckDemoLogin) {
        $auth = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"manager","password":"manager123"}' -TimeoutSec 20
        if (-not $auth.token -or $auth.role -ne 'MANAGER') { throw 'Fresh demo manager login failed.' }
        $patientPage = Invoke-RestMethod -Uri "$baseUrl/api/patients?size=1" -Headers @{Authorization="Bearer $($auth.token)"} -TimeoutSec 20
        $patients = [int]$patientPage.totalItems
        $loginPassed = $true
    }
    Write-Host 'SMOKE_OK frontend=200 protectedBackend=401'
    [pscustomobject]@{ frontendHttpStatus=200; protectedApiStatus=$protectedStatus; demoLoginPassed=$loginPassed; patientCount=$patients }
} finally {
    if ($forward -and -not $forward.HasExited) { Stop-Process -Id $forward.Id; $forward.WaitForExit() }
    foreach ($path in @($outLog,$errLog)) { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path } }
}
