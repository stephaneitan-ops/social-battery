@echo off
set "SB=%LOCALAPPDATA%\SocialBattery"
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; New-Item -ItemType Directory -Force -Path $env:SB | Out-Null; try { Invoke-WebRequest 'https://raw.githubusercontent.com/stephaneitan-ops/social-battery/main/installer/Installer.ps1' -OutFile \"$env:SB\Installer.ps1\" -UseBasicParsing } catch { Copy-Item '%~dp0Installer.ps1' \"$env:SB\Installer.ps1\" -Force }"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SB%\Installer.ps1" -Mode install -Here "%~dp0."
