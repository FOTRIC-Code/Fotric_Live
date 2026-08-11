# Copy published IRtekNetSDK Maven artifacts into this app's local repo.
$src = "F:\Volga.IRtekNetSDK\android\IRtekNetSDK\build\repo"
$dst = Join-Path $PSScriptRoot "..\repo"
$props = Join-Path $PSScriptRoot "..\gradle.properties"
if (-not (Test-Path "$src\com\irtek\netsdk")) {
    Write-Error "SDK repo not found: $src`nRun :IRtekNetSDK:publishReleasePublicationToLocalBuildRepository first."
    exit 1
}
New-Item -ItemType Directory -Force -Path $dst | Out-Null
Copy-Item -Path "$src\*" -Destination $dst -Recurse -Force
$meta = Get-Content "$src\com\irtek\netsdk\maven-metadata.xml" -Raw
if ($meta -match '<release>([^<]+)</release>') {
    $ver = $Matches[1]
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $lines = @(Get-Content $props)
    $found = $false
    $out = foreach ($line in $lines) {
        if ($line -match '^IRTEK_NETSDK_VERSION=') { $found = $true; "IRTEK_NETSDK_VERSION=$ver" } else { $line }
    }
    if (-not $found) { $out = @($out) + "IRTEK_NETSDK_VERSION=$ver" }
    [IO.File]::WriteAllLines($props, $out, $utf8)
    Write-Host "IRTEK_NETSDK_VERSION=$ver"
}
Write-Host "Copied SDK Maven artifacts to $dst"
Get-ChildItem "$dst\com\irtek\netsdk" -Recurse -File | Select-Object FullName, Length
