# Builds ClinicIdCardAgent-<version>.msi. Run on Windows with a JDK 17+ and WiX Toolset 3.x on PATH.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
mvn -q package
Pop-Location
jpackage --type msi --name ClinicIdCardAgent --app-version 1.0.0 `
  --input "$root\target" --main-jar id-card-agent.jar --main-class com.sabyeguyz.idcard.agent.AgentMain `
  --add-modules java.smartcardio,java.net.http,java.desktop,java.logging `
  --arguments run --win-shortcut --win-menu --win-per-user-install `
  --dest "$root\target\installer"
Write-Host "Installer: $root\target\installer"
