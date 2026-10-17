# Verifies the packaged APK against the libxposed API 102 packaging rules.
param(
    [string]$Apk = "dist\MBGA-v1.5.0-libxposed102-vc170.apk",
    [string]$Out = "dist\VERIFY.md"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (Test-Path "$root\tools\jdk\jdk-17.0.2") {
    $env:JAVA_HOME = "$root\tools\jdk\jdk-17.0.2"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
$bt = "$root\tools\android-sdk\build-tools\34.0.0"
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python) { throw "python not found in PATH" }

& $python "$root\tools\py\dump_xposed.py" (Resolve-Path $Apk)
