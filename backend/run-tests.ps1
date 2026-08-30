$ErrorActionPreference = 'Stop'

$backendRoot = $PSScriptRoot
$java17 = 'C:\Program Files\Java\jdk-17'

if (Test-Path "$java17\bin\java.exe") {
    $env:JAVA_HOME = $java17
    $filteredPath = (($env:Path -split ';') | Where-Object { $_ -notmatch 'Java\\jdk-25|Java\\latest' }) -join ';'
    $env:Path = "$java17\bin;$filteredPath"
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $java25 = 'C:\Program Files\Java\jdk-25'
    if (Test-Path "$java25\bin\java.exe") {
        $env:JAVA_HOME = $java25
        $filteredPath = (($env:Path -split ';') | Where-Object { $_ -notmatch 'Java\\jdk-17|Java\\latest' }) -join ';'
        $env:Path = "$java25\bin;$filteredPath"
        Write-Warning 'JDK 17 was not found; using JDK 25. The Surefire setting enables Mockito agent loading.'
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    throw 'No supported JDK was found. Install JDK 17 or JDK 25 and set JAVA_HOME.'
}

Set-Location $backendRoot
& java -version

$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $maven) {
    throw 'Maven was not found in PATH. Install Maven and add its bin folder to PATH.'
}

& mvn test @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
