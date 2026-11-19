$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$keystoreDir = Join-Path $projectRoot "keystore"
$keystorePath = Join-Path $keystoreDir "muddy-soccer-release.jks"
$propertiesPath = Join-Path $projectRoot "keystore.properties"

if (Test-Path $keystorePath) {
    Write-Host "Keystore already exists: $keystorePath"
    exit 0
}

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "D:\Tools\jdk-21\jdk-21.0.12.1+1"
}
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    throw "keytool not found. Set JAVA_HOME first."
}

New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
$alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$random.GetBytes($bytes)
$password = -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })

& $keytool -genkeypair -v `
    -keystore $keystorePath `
    -alias muddy-soccer `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $password `
    -keypass $password `
    -dname "CN=Muddy Soccer, OU=Campus Football, O=Campus Football, L=Shanghai, ST=Shanghai, C=CN"

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE"
}

$properties = @(
    "storeFile=keystore/muddy-soccer-release.jks",
    "storePassword=$password",
    "keyAlias=muddy-soccer",
    "keyPassword=$password"
)
[System.IO.File]::WriteAllLines($propertiesPath, $properties, [System.Text.UTF8Encoding]::new($false))
Write-Host "Created $keystorePath"
Write-Host "Signing properties written to $propertiesPath"
Write-Host "Back up both files; losing the keystore prevents in-place app updates."