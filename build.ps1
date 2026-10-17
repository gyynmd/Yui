# Builds the module APK. Expects JDK 17 / Android SDK / Gradle 8.7 under tools/
# (see README "构建" section), or set JAVA_HOME / ANDROID_HOME yourself.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (Test-Path "$root\tools\jdk\jdk-17.0.2") {
    $env:JAVA_HOME = "$root\tools\jdk\jdk-17.0.2"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if (Test-Path "$root\tools\android-sdk") { $env:ANDROID_HOME = "$root\tools\android-sdk" }
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (Test-Path "$root\tools\android-user") { $env:ANDROID_USER_HOME = "$root\tools\android-user" }
if (Test-Path "$root\tools\gradle-home") { $env:GRADLE_USER_HOME = "$root\tools\gradle-home" }
if (Test-Path "$root\tools\tmp") {
    $env:TEMP = "$root\tools\tmp"; $env:TMP = $env:TEMP
}

# Prefer a local Gradle; fall back to a gradle on PATH.
$gradle = "$root\tools\gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $gradle)) { $gradle = "gradle" }

& $gradle -p $root assembleRelease --no-daemon --no-watch-fs --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle assembleRelease failed with exit code $LASTEXITCODE" }

New-Item -ItemType Directory -Force -Path "$root\dist" | Out-Null
$apk = "$root\app\build\outputs\apk\release\app-release.apk"
Copy-Item $apk "$root\dist\MBGA-v1.5.0-libxposed102-vc170.apk" -Force
Get-Item "$root\dist\MBGA-v1.5.0-libxposed102-vc170.apk" | Select-Object FullName, Length
