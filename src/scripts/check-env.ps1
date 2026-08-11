$ErrorActionPreference = 'Stop'

if (Test-Path 'src\gradlew.bat') {
    Set-Location 'src'
}

Write-Host "==== Java ===="
java -version
Write-Host "PWD=$pwd"

$sdkDir = $env:ANDROID_SDK_ROOT
if (-not $sdkDir) { $sdkDir = $env:ANDROID_HOME }
Write-Host "ANDROID_SDK_ROOT=$($env:ANDROID_SDK_ROOT)"
Write-Host "ANDROID_HOME=$($env:ANDROID_HOME)"
if (-not $sdkDir) {
    throw "Set ANDROID_SDK_ROOT on the agent (typical: $env:LOCALAPPDATA\Android\Sdk)"
}
if (-not (Test-Path (Join-Path $sdkDir 'platform-tools'))) {
    throw "Android SDK not found at $sdkDir (need platform-tools)"
}

if (-not (Test-Path 'gradlew.bat')) {
    throw "gradlew.bat not found (looked in checkout root and src)"
}

$verLine = Select-String -Path 'gradle.properties' -Pattern '^IRTEK_NETSDK_VERSION=(.+)$' | Select-Object -First 1
if (-not $verLine) { throw "IRTEK_NETSDK_VERSION missing in gradle.properties" }
$ver = $verLine.Matches[0].Groups[1].Value.Trim()
$aar = "repo\com\irtek\netsdk\$ver\netsdk-$ver.aar"
if (-not (Test-Path $aar)) { throw "missing AAR $aar" }

Write-Host "Using SDK_DIR=$sdkDir"
Write-Host "Environment OK, netsdk=$ver"
