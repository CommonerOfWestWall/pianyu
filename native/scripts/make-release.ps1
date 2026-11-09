$ErrorActionPreference = 'Stop'
$strip = 'C:/android_sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
$input = Join-Path $PSScriptRoot 'spektralab_engine.so'
$output = Join-Path $PSScriptRoot 'spektralab_engine.release.so'
if (!(Test-Path $input)) { throw "missing $input; run build-android.ps1 first" }
Copy-Item $input $output -Force
& $strip --strip-unneeded $output
if ($LASTEXITCODE -ne 0) { throw "llvm-strip failed with exit code $LASTEXITCODE" }
$debug = (Get-Item $input).Length; $release = (Get-Item $output).Length
Write-Output "debug_bytes=$debug release_bytes=$release"
