# Builds ClinicIdCardAgent-<version>.msi. Run on Windows with a JDK 17+ and WiX Toolset 3.x on PATH.
#
#   -AllowedOrigins  the address(es) of the clinic's web app, comma separated, e.g. "https://erp.clinic.example".
#                    Only these sites may talk to the agent on this PC (to show its state and pair it from the web page).
#                    The pairing button in the web app does not work without it.
param([string]$AllowedOrigins = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
mvn -q package
Pop-Location
$javaOptions = @()
if ($AllowedOrigins) { $javaOptions = @('--java-options', "-Dclinic.allowedOrigins=$AllowedOrigins") }
else { Write-Warning 'No -AllowedOrigins given: the web page will not be able to pair this agent.' }
# jdk.httpserver serves the local address the web page talks to
jpackage --type msi --name ClinicIdCardAgent --app-version 1.0.0 `
  --input "$root\target" --main-jar id-card-agent.jar --main-class com.sabyeguyz.idcard.agent.AgentMain `
  --add-modules java.smartcardio,java.net.http,java.desktop,java.logging,jdk.httpserver `
  --arguments run --win-shortcut --win-menu --win-per-user-install `
  @javaOptions `
  --dest "$root\target\installer"
Write-Host "Installer: $root\target\installer"
