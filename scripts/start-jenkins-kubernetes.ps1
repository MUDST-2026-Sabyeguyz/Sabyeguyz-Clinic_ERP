param(
    [string]$JenkinsUrl = 'http://127.0.0.1:8085',
    [string]$JenkinsContainer,
    [string]$AgentRoot = (Join-Path $env:LOCALAPPDATA 'ClinicJenkinsAgent')
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$repoPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoPath
$JenkinsUrl = $JenkinsUrl.TrimEnd('/')
foreach ($tool in @('docker', 'kubectl', 'git')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required on this Windows machine." }
}
& kubectl --context docker-desktop get nodes
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop Kubernetes must be Ready on this machine.' }
& git rev-parse --verify refs/heads/main
if ($LASTEXITCODE -ne 0) { throw 'Pull the main branch before starting Jenkins deployment.' }

function Run-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker failed: $($args -join ' ')" }
}
if (-not $JenkinsContainer) {
    $candidates = @(Run-Docker ps -a --filter 'publish=8085' --format '{{.Names}}')
    if ($candidates.Count -ne 1) { throw 'Specify the existing Jenkins container with -JenkinsContainer <name>.' }
    $JenkinsContainer = $candidates[0]
}
$controller = Run-Docker inspect $JenkinsContainer | ConvertFrom-Json
if (-not ($controller[0].Config.Env -contains 'JENKINS_HOME=/var/jenkins_home')) {
    throw 'The selected container is not the expected Jenkins controller.'
}
$AgentRoot = [IO.Path]::GetFullPath($AgentRoot)
[IO.Directory]::CreateDirectory($AgentRoot) | Out-Null
$java = Join-Path $AgentRoot 'jre/bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    $zip = Join-Path $AgentRoot 'jre.zip'
    Write-Host 'Downloading the Windows Java 21 runtime for the Jenkins agent...'
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
    $agentPid = 0
    if ([int]::TryParse(([IO.File]::ReadAllText($pidFile)).Trim(), [ref]$agentPid)) {
        $oldAgent = Get-CimInstance Win32_Process -Filter "ProcessId=$agentPid" -ErrorAction SilentlyContinue
        if ($oldAgent -and $oldAgent.CommandLine -and $oldAgent.CommandLine.Contains($jar)) {
            Stop-Process -Id $agentPid
        }
    }
}
$bootstrapConfig = Join-Path $AgentRoot 'bootstrap.json'
$config = @{ repoPath = $repoPath; agentRoot = $AgentRoot } | ConvertTo-Json
[IO.File]::WriteAllText($bootstrapConfig, $config, (New-Object Text.UTF8Encoding($false)))
Run-Docker cp $bootstrapConfig "${JenkinsContainer}:/var/jenkins_home/clinic-kubernetes-bootstrap.json"
Run-Docker cp (Join-Path $repoPath 'Jenkinsfile.kubernetes') "${JenkinsContainer}:/var/jenkins_home/clinic-kubernetes.Jenkinsfile"
# docker cp works for stopped containers; starting it first also creates init.groovy.d.
Run-Docker start $JenkinsContainer
Run-Docker exec $JenkinsContainer mkdir -p /var/jenkins_home/init.groovy.d
Run-Docker exec $JenkinsContainer rm -f /var/jenkins_home/secrets/clinic-present-agent
Run-Docker cp (Join-Path $repoPath 'deploy/jenkins-kubernetes/bootstrap.groovy') "${JenkinsContainer}:/var/jenkins_home/init.groovy.d/99-clinic-kubernetes.groovy"
Write-Host 'Restarting Jenkins once to register the Windows agent and deploy job...'
Run-Docker restart $JenkinsContainer
$deadline = (Get-Date).AddMinutes(5)
do {
    & docker exec $JenkinsContainer test -s /var/jenkins_home/secrets/clinic-present-agent 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($LASTEXITCODE -ne 0) { throw "Jenkins setup did not finish. Check: docker logs $JenkinsContainer" }
$secretFile = Join-Path $AgentRoot 'agent.secret'
Run-Docker cp "${JenkinsContainer}:/var/jenkins_home/secrets/clinic-present-agent" $secretFile
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $secretFile /inheritance:r /grant:r "$($owner):(F)" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict permissions of the agent secret file.' }
Run-Docker exec $JenkinsContainer rm -f /var/jenkins_home/init.groovy.d/99-clinic-kubernetes.groovy
$jarReady = $false
$downloadDeadline = (Get-Date).AddMinutes(2)
do {
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "$JenkinsUrl/jnlpJars/agent.jar" -OutFile $jar
        $jarReady = (Get-Item -LiteralPath $jar).Length -gt 100000
    } catch { $jarReady = $false }
    if (-not $jarReady) { Start-Sleep -Seconds 3 }
} while (-not $jarReady -and (Get-Date) -lt $downloadDeadline)
if (-not $jarReady) { throw "Cannot download the agent from $JenkinsUrl. Check the Jenkins URL and startup log." }
$agentArgs = @('-jar', ('"' + $jar + '"'), '-url', ('"' + $JenkinsUrl + '"'), '-secret', ('"@' + $secretFile + '"'), '-name', 'clinic-present', '-webSocket', '-workDir', ('"' + $AgentRoot + '"'))
$agentProcess = Start-Process -FilePath $java -ArgumentList $agentArgs -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $AgentRoot 'agent.stdout.log') `
    -RedirectStandardError (Join-Path $AgentRoot 'agent.stderr.log')
[IO.File]::WriteAllText($pidFile, [string]$agentProcess.Id)
Write-Host "Agent started. Build and deployment: $JenkinsUrl/job/Clinic-Kubernetes/"
Write-Host "Agent log: $(Join-Path $AgentRoot 'agent.stderr.log')"
Write-Host 'After the build succeeds, run: kubectl --context docker-desktop -n clinic-present port-forward service/frontend 38001:80'
Start-Process "$JenkinsUrl/job/Clinic-Kubernetes/"
