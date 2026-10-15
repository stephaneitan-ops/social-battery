$ErrorActionPreference = 'Continue'
$here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$base  = Join-Path $env:LOCALAPPDATA 'SocialBattery'
$adb   = Join-Path $base 'platform-tools\adb.exe'

function Pause-Exit($c) { Write-Host ""; Read-Host "Appuie sur Entree pour fermer"; exit $c }
function Get-Watch {
    (& $adb devices) -split "`r?`n" | Where-Object { $_ -match "`tdevice$" } | Select-Object -First 1
}

Write-Host ""
Write-Host "=== Installation Social Battery sur ta Galaxy Watch ===" -ForegroundColor Cyan
Write-Host ""

# 1. Outil d'installation Google (une seule fois)
if (-not (Test-Path $adb)) {
    Write-Host "Telechargement de l'outil d'installation (une seule fois)..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $zip = Join-Path $env:TEMP 'platform-tools.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
        New-Item -ItemType Directory -Force -Path $base | Out-Null
        Expand-Archive $zip -DestinationPath $base -Force
    } catch {
        Write-Host "Echec du telechargement. Verifie ta connexion internet." -ForegroundColor Red
        Pause-Exit 1
    }
}

& $adb start-server *> $null
Start-Sleep -Seconds 3
$dev = Get-Watch

# 2. Association avec la montre (seulement si elle n'est pas deja reconnue)
if (-not $dev) {
    Write-Host "IMPORTANT : coupe le Bluetooth de la montre pendant l'installation" -ForegroundColor Yellow
    Write-Host "(sinon elle quitte le Wi-Fi en cours de route). Tu le rallumeras apres."
    Write-Host ""
    Write-Host "Sur la MONTRE :" -ForegroundColor Yellow
    Write-Host "  Parametres > Options de developpement > Debogage sans fil"
    Write-Host "  > Associer un nouvel appareil"
    Write-Host "  Laisse cet ecran ouvert pendant que tu remplis ce qui suit."
    Write-Host ""
    $paired = $false
    for ($try = 1; $try -le 3 -and -not $paired; $try++) {
        $ip    = (Read-Host "Adresse IP affichee (exemple : 192.168.1.42)").Trim()
        $pport = (Read-Host "Port affiche apres les deux-points (exemple : 37155)").Trim()
        $code  = (Read-Host "Code a 6 chiffres").Trim()
        $out = (& $adb pair "$($ip):$($pport)" $code 2>&1 | Out-String)
        if ($out -match "Successfully paired") {
            Write-Host "Association reussie." -ForegroundColor Green
            $paired = $true
        } else {
            Write-Host "Association refusee : $($out.Trim())" -ForegroundColor Red
            Write-Host "Sur la montre, ferme puis rouvre 'Associer un nouvel appareil' : un NOUVEAU code et port s'affichent." -ForegroundColor Yellow
            Write-Host ""
        }
    }
    if (-not $paired) { Pause-Exit 1 }

    Write-Host "Connexion a la montre..."
    for ($i = 0; $i -lt 6 -and -not $dev; $i++) { Start-Sleep -Seconds 2; $dev = Get-Watch }

    if (-not $dev) {
        Write-Host ""
        Write-Host "Presque fini ! Sur la montre, reviens sur l'ecran 'Debogage sans fil'." -ForegroundColor Yellow
        Write-Host "  Regarde 'Adresse IP et port' : note le nombre apres les deux-points."
        $cport = (Read-Host "Ce nouveau port").Trim()
        & $adb connect "$($ip):$($cport)"
        Start-Sleep -Seconds 2
        $dev = Get-Watch
    }
}

if (-not $dev) {
    Write-Host ""
    Write-Host "Montre introuvable. Verifie que la montre et le PC sont sur le meme Wi-Fi," -ForegroundColor Red
    Write-Host "puis relance l'installateur." -ForegroundColor Red
    Pause-Exit 1
}

# 3. Installation
$serial = ($dev -split "`t")[0]
Write-Host ""
Write-Host "Montre connectee. Installation en cours (1 a 2 minutes)..." -ForegroundColor Green
& $adb -s $serial install -r (Join-Path $here 'SocialBattery-app.apk')
& $adb -s $serial install -r (Join-Path $here 'SocialBattery-cadran.apk')
& $adb -s $serial shell am start -n fr.stephane.socialbattery/.MainActivity *> $null

Write-Host ""
Write-Host "C'est installe !" -ForegroundColor Green
Write-Host "  1. Sur la montre, l'appli s'est ouverte : AUTORISE les capteurs."
Write-Host "  2. Appui long sur ton cadran > fais defiler > choisis 'Social Battery'."
Pause-Exit 0
