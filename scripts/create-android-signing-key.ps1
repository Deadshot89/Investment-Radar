$ErrorActionPreference = "Stop"

$alias = "investment-radar"
$keystore = Join-Path $PWD "investment-radar-release.jks"

Write-Host "Erstelle dauerhaften Android-Signierschlüssel..."
Write-Host "WICHTIG: Keystore und Passwörter dauerhaft sichern. Ohne sie sind spätere Updates nicht möglich."

keytool -genkeypair `
  -v `
  -keystore $keystore `
  -alias $alias `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -storetype JKS

if ($LASTEXITCODE -ne 0) { throw "keytool fehlgeschlagen" }

$bytes = [System.IO.File]::ReadAllBytes($keystore)
$b64 = [Convert]::ToBase64String($bytes)
$b64Path = Join-Path $PWD "ANDROID_KEYSTORE_BASE64.txt"
[System.IO.File]::WriteAllText($b64Path, $b64)

Write-Host ""
Write-Host "Erstellt: $keystore"
Write-Host "Erstellt: $b64Path"
Write-Host "GitHub Secret ANDROID_KEY_ALIAS = $alias"
Write-Host "Die beim keytool gewählten Passwörter als ANDROID_KEYSTORE_PASSWORD und ANDROID_KEY_PASSWORD in GitHub speichern."
