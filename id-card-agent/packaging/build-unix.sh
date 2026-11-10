#!/bin/sh
# Builds the installer for the OS it runs on: .pkg on macOS, .deb on Linux (which depends on pcscd).
# Needs a JDK 17+ (jpackage) and, on Linux, fakeroot and dpkg-dev.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
(cd "$ROOT" && mvn -q package)

COMMON="--name ClinicIdCardAgent --app-version 1.0.0 --input $ROOT/target --main-jar id-card-agent.jar \
  --main-class com.sabyeguyz.idcard.agent.AgentMain \
  --add-modules java.smartcardio,java.net.http,java.desktop,java.logging --arguments run --dest $ROOT/target/installer"

case "$(uname -s)" in
  Darwin) jpackage --type pkg $COMMON ;;
  Linux)  jpackage --type deb $COMMON --linux-package-deps pcscd --linux-shortcut ;;
  *) echo "Run packaging/build-windows.ps1 on Windows"; exit 1 ;;
esac
echo "Installer written to $ROOT/target/installer"
