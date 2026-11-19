param(
    [switch]$DebugBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$repoRoot = Split-Path -Parent $projectRoot
$versionName = "1.0.0"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "D:\Tools\jdk-21\jdk-21.0.12.1+1"
}
if (-not $env:ANDROID_HOME) {
    $candidates = @(
        "D:\Tools\android-sdk",
        "$env:LOCALAPPDATA\Android\Sdk"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "platforms\android-35\android.jar")) {
            $env:ANDROID_HOME = $candidate
            break
        }
    }
}
if (-not $env:ANDROID_HOME) {
    throw "Android SDK not found. Set ANDROID_HOME first."
}

Push-Location $projectRoot
try {
    if ($DebugBuild) {
        & ".\gradlew.bat" assembleDebug --no-daemon
        $source = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
        $suffix = "-debug"
    } else {
        & ".\gradlew.bat" assembleRelease --no-daemon
        $source = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
        $suffix = ""
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$outputDir = Join-Path $repoRoot "output\android"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$outputName = "泥地soccer-v$versionName$suffix.apk"
$destination = Join-Path $outputDir $outputName
Copy-Item -LiteralPath $source -Destination $destination -Force

$distDir = Join-Path $projectRoot "dist"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$distName = "muddy-soccer-v$versionName$suffix.apk"
$distPath = Join-Path $distDir $distName
Copy-Item -LiteralPath $source -Destination $distPath -Force

$file = Get-Item -LiteralPath $distPath
$hash = Get-FileHash -LiteralPath $distPath -Algorithm SHA256
[System.IO.File]::WriteAllText((Join-Path $distDir "SHA256SUMS.txt"), ("{0}  {1}`n" -f $hash.Hash.ToLowerInvariant(), $distName), [System.Text.UTF8Encoding]::new($false))

[pscustomobject]@{
    Apk = $file.FullName
    SizeMB = [math]::Round($file.Length / 1MB, 2)
    SHA256 = $hash.Hash
} | Format-List