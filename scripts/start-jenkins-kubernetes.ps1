param(
  [string]$JenkinsUrl = 'http://127.0.0.1:8086',
  [string]$SourceBranch,
  [string]$AgentRoot = (Join-Path $env:USERPROFILE 'Desktop\ClinicJenkinsAgent')
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoPath
foreach ($tool in @('docker','kubectl','git')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required." } }
if (-not $SourceBranch) { $SourceBranch = (& git branch --show-current).Trim() }
& git check-ref-format --branch $SourceBranch *> $null
if ($LASTEXITCODE -ne 0) { throw "Invalid local branch: $SourceBranch" }
& git rev-parse --verify "refs/heads/$SourceBranch^{commit}" *> $null
if ($LASTEXITCODE -ne 0) { throw "Branch '$SourceBranch' does not exist locally." }

$deadline = (Get-Date).AddMinutes(20)
do {
  & kubectl --context docker-desktop get nodes *> $null
  if ($LASTEXITCODE -eq 0) { break }
  Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop Kubernetes did not become Ready within 20 minutes.' }

[IO.Directory]::CreateDirectory($AgentRoot) | Out-Null
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $AgentRoot /inheritance:r /grant:r "$($owner):(OI)(CI)(F)" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not secure the local Jenkins agent directory.' }
& (Join-Path $PSScriptRoot 'prepare-kubernetes-credentials.ps1') -Context docker-desktop -Namespace clinic-present -AgentRoot $AgentRoot
if ($LASTEXITCODE -ne 0) { throw 'Kubernetes scoped credential setup failed.' }

$composeFile = Join-Path $repoPath 'deploy/jenkins-kubernetes/compose.yaml'
$adminPasswordPath = Join-Path $AgentRoot 'jenkins-admin-password.txt'
if (-not (Test-Path -LiteralPath $adminPasswordPath)) {
  $random = New-Object byte[] 36
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($random) } finally { $rng.Dispose() }
  $password = [Convert]::ToBase64String($random).TrimEnd('=').Replace('+','A').Replace('/','B')
  [IO.File]::WriteAllText($adminPasswordPath,$password,(New-Object Text.UTF8Encoding($false)))
  & icacls $adminPasswordPath /inheritance:r /grant:r "$($owner):(F)" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Could not secure the local Jenkins administrator password.' }
}
$env:CLINIC_JENKINS_ADMIN_PASSWORD = [IO.File]::ReadAllText($adminPasswordPath).Trim()
try {
  & docker compose -f $composeFile up --build -d jenkins
  if ($LASTEXITCODE -ne 0) { throw 'Local Jenkins startup failed.' }
  $container = (& docker compose -f $composeFile ps -q jenkins).Trim()
  if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'Could not find the local Jenkins container.' }
}
finally { Remove-Item Env:\CLINIC_JENKINS_ADMIN_PASSWORD -ErrorAction SilentlyContinue }
$JenkinsUrl = $JenkinsUrl.TrimEnd('/')
$deadline = (Get-Date).AddMinutes(10)
do {
  try { $response = Invoke-WebRequest -UseBasicParsing -Uri "$JenkinsUrl/login" -TimeoutSec 5; if ($response.StatusCode -eq 200) { break } }
  catch { Start-Sleep -Seconds 3 }
} while ((Get-Date) -lt $deadline)
if ((Get-Date) -ge $deadline) { throw "Jenkins did not become ready. Inspect docker logs $container." }

$config = @{ repoPath=$repoPath; agentRoot=$AgentRoot; sourceBranch=$SourceBranch } | ConvertTo-Json
$configPath = Join-Path $AgentRoot 'bootstrap.json'
[IO.File]::WriteAllText($configPath,$config,(New-Object Text.UTF8Encoding($false)))
& docker cp $configPath ($container + ':/var/jenkins_home/clinic-kubernetes-bootstrap.json')
& docker cp (Join-Path $AgentRoot 'clinic-kubeconfig.json') ($container + ':/var/jenkins_home/clinic-local-kubeconfig.json')
& docker cp (Join-Path $repoPath 'Jenkinsfile.kubernetes') ($container + ':/var/jenkins_home/clinic-kubernetes.Jenkinsfile')
if ($LASTEXITCODE -ne 0) { throw 'Could not stage Jenkins pipeline configuration.' }
& docker exec $container mkdir -p /var/jenkins_home/init.groovy.d
& docker exec $container rm -f /var/jenkins_home/secrets/clinic-present-agent
& docker cp (Join-Path $repoPath 'deploy/jenkins-kubernetes/bootstrap.groovy') ($container + ':/var/jenkins_home/init.groovy.d/99-clinic-kubernetes.groovy')
& docker cp (Join-Path $repoPath 'deploy/jenkins-kubernetes/init.groovy.d/00-local-security.groovy') ($container + ':/var/jenkins_home/init.groovy.d/00-local-security.groovy')
if ($LASTEXITCODE -ne 0) { throw 'Could not stage Jenkins bootstrap.' }
& docker restart $container
if ($LASTEXITCODE -ne 0) { throw 'Jenkins restart failed.' }

$deadline = (Get-Date).AddMinutes(5)
do {
  & docker exec $container test -s /var/jenkins_home/secrets/clinic-present-agent 2>$null
  if ($LASTEXITCODE -eq 0) { break }
  Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($LASTEXITCODE -ne 0) { throw "Jenkins bootstrap failed. Inspect docker logs $container." }
$secretFile = Join-Path $AgentRoot 'agent.secret'
$agentSecret = (& docker exec $container cat /var/jenkins_home/secrets/clinic-present-agent).Trim()
if ($LASTEXITCODE -ne 0 -or -not $agentSecret) { throw 'Could not retrieve the Windows agent secret.' }
[IO.File]::WriteAllText($secretFile,$agentSecret,(New-Object Text.UTF8Encoding($false)))
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $secretFile /inheritance:r /grant:r "$($owner):(F)" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not secure the agent credential file.' }
& docker exec $container rm -f /var/jenkins_home/init.groovy.d/99-clinic-kubernetes.groovy /var/jenkins_home/clinic-kubernetes-bootstrap.json /var/jenkins_home/clinic-kubernetes.Jenkinsfile /var/jenkins_home/clinic-local-kubeconfig.json
Remove-Item -LiteralPath (Join-Path $AgentRoot 'clinic-kubeconfig.json') -Force -ErrorAction SilentlyContinue

$java = Join-Path $AgentRoot 'jre/bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) {
  $zip = Join-Path $AgentRoot 'jre.zip'
  Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk' -OutFile $zip
  $expanded = Join-Path $AgentRoot 'runtime-download'
  Expand-Archive -LiteralPath $zip -DestinationPath $expanded -Force
  $javaFile = Get-ChildItem -LiteralPath $expanded -Filter java.exe -Recurse | Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
  if (-not $javaFile) { throw 'Java 21 runtime download did not contain java.exe.' }
  Move-Item -LiteralPath $javaFile.Directory.Parent.FullName -Destination (Join-Path $AgentRoot 'jre')
  Remove-Item -LiteralPath $zip
}
$jar = Join-Path $AgentRoot 'agent.jar'
$pidFile = Join-Path $AgentRoot 'agent.pid'
if (Test-Path -LiteralPath $pidFile) {
  $oldPid = 0
  if ([int]::TryParse(([IO.File]::ReadAllText($pidFile)).Trim(),[ref]$oldPid)) {
    $oldAgent = Get-CimInstance Win32_Process -Filter "ProcessId=$oldPid" -ErrorAction SilentlyContinue
    if ($oldAgent -and $oldAgent.CommandLine -and $oldAgent.CommandLine.Contains($jar)) {
      Stop-Process -Id $oldPid
      Wait-Process -Id $oldPid -Timeout 10 -ErrorAction SilentlyContinue
      $remainingAgent = Get-CimInstance Win32_Process -Filter "ProcessId=$oldPid" -ErrorAction SilentlyContinue
      if ($remainingAgent -and $remainingAgent.CommandLine -and $remainingAgent.CommandLine.Contains($jar)) {
        throw 'The previous local Jenkins agent did not stop.'
      }
    }
  }
}
Invoke-WebRequest -UseBasicParsing -Uri "$JenkinsUrl/jnlpJars/agent.jar" -OutFile $jar
if ((Get-Item -LiteralPath $jar).Length -le 100000) { throw 'Invalid Jenkins agent.jar.' }
$agentArgs = @('-jar',('"' + $jar + '"'),'-url',('"' + $JenkinsUrl + '"'),'-secret',('"@' + $secretFile + '"'),'-name','clinic-present','-webSocket','-workDir',('"' + $AgentRoot + '"'))
$process = Start-Process -FilePath $java -ArgumentList $agentArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $AgentRoot 'agent.stdout.log') -RedirectStandardError (Join-Path $AgentRoot 'agent.stderr.log')
[IO.File]::WriteAllText($pidFile,[string]$process.Id)
Write-Host "Jenkins: $JenkinsUrl/job/Clinic-Kubernetes/"
Write-Host "Monitoring local branch '$SourceBranch' every minute."
Write-Host "Local Jenkins admin username: admin; password file: $adminPasswordPath"
Write-Host "Agent log: $(Join-Path $AgentRoot 'agent.stderr.log')"
Start-Process "$JenkinsUrl/job/Clinic-Kubernetes/"
