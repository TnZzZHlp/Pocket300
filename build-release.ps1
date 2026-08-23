[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+(-(alpha|beta|rc)(\.\d+)?)?$')]
    [string]$VersionName,
    [string]$KeystorePath = "$env:USERPROFILE\pocket300-release.jks",
    [string]$KeyAlias = "pocket300"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Set-Location $PSScriptRoot

if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Signing keystore not found: $KeystorePath"
}

$storeSecret = Read-Host "Keystore password" -AsSecureString
$keySecret = Read-Host "Key password (press Enter to reuse the keystore password)" -AsSecureString
if ($keySecret.Length -eq 0) {
    $keySecret = $storeSecret
}

$signingVariables = @{
    RELEASE_KEYSTORE_PATH = (Resolve-Path -LiteralPath $KeystorePath).Path
    RELEASE_STORE_PASSWORD = [pscredential]::new("store", $storeSecret).GetNetworkCredential().Password
    RELEASE_KEY_ALIAS = $KeyAlias
    RELEASE_KEY_PASSWORD = [pscredential]::new("key", $keySecret).GetNetworkCredential().Password
}
$previousVariables = @{}
foreach ($name in $signingVariables.Keys) {
    $previousVariables[$name] = [Environment]::GetEnvironmentVariable($name)
    [Environment]::SetEnvironmentVariable($name, $signingVariables[$name])
}

try {
    $arguments = @("assembleRelease")
    if ($VersionName) {
        $arguments += "-PversionName=$VersionName"
    }
    & "$PSScriptRoot\gradlew.bat" @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle release build failed with exit code $LASTEXITCODE"
    }
} finally {
    foreach ($name in $previousVariables.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousVariables[$name])
    }
}

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
    throw "Signed release APK not found: $apk"
}
Write-Host "Signed release APK: $apk"
