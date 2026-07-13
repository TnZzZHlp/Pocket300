[CmdletBinding()]
param(
    [string]$KeystorePath = "$env:USERPROFILE\pocket300-release.jks",
    [string]$KeyAlias = "pocket300",
    [string]$OutputPath = "app\build\outputs\apk\release\app-release-signed.apk"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = $PSScriptRoot
Set-Location $repositoryRoot

if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Signing keystore not found: $KeystorePath"
}

$sdkRoot = @(
    $env:ANDROID_SDK_ROOT
    $env:ANDROID_HOME
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) } | Select-Object -First 1

if (-not $sdkRoot) {
    $localProperties = Join-Path $repositoryRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkEntry = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkEntry) {
            # local.properties uses Java properties escaping on Windows, for
            # example: C\:\\Users\\name\\AppData\\Local\\Android\\Sdk
            $sdkRoot = ($sdkEntry -replace '^sdk\.dir=', '')
            $sdkRoot = ($sdkRoot -replace '\\:', ':') -replace '\\\\', '\'
        }
    }
}

if (-not $sdkRoot -or -not (Test-Path -LiteralPath $sdkRoot -PathType Container)) {
    throw "Android SDK not found. Set ANDROID_SDK_ROOT or sdk.dir in local.properties."
}

$buildToolsRoot = Join-Path $sdkRoot "build-tools"
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "apksigner.bat") } |
    Select-Object -First 1

if (-not $buildTools) {
    throw "apksigner.bat not found under $buildToolsRoot"
}

$unsignedApk = Join-Path $repositoryRoot "app\build\outputs\apk\release\app-release-unsigned.apk"
$signedApk = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputPath))
$signedDirectory = Split-Path -Parent $signedApk

Write-Host "Building release APK..."
& "$repositoryRoot\gradlew.bat" assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Gradle release build failed with exit code $LASTEXITCODE"
}
if (-not (Test-Path -LiteralPath $unsignedApk -PathType Leaf)) {
    throw "Unsigned release APK not found: $unsignedApk"
}

New-Item -ItemType Directory -Path $signedDirectory -Force | Out-Null
if (Test-Path -LiteralPath $signedApk) {
    Remove-Item -LiteralPath $signedApk -Force
}

$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
Write-Host "Signing APK with alias '$KeyAlias'. Enter the keystore password when prompted..."
& $apksigner sign `
    --ks $KeystorePath `
    --ks-key-alias $KeyAlias `
    --out $signedApk `
    $unsignedApk
if ($LASTEXITCODE -ne 0) {
    throw "APK signing failed with exit code $LASTEXITCODE"
}

Write-Host "Verifying APK signature..."
& $apksigner verify --verbose --print-certs $signedApk
if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed with exit code $LASTEXITCODE"
}

Write-Host "Signed release APK: $signedApk"
